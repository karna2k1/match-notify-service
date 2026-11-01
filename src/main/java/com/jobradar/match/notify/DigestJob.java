package com.jobradar.match.notify;

import com.jobradar.match.match.Match;
import com.jobradar.match.match.MatchRepository;
import com.jobradar.match.profile.MatchingProfile;
import com.jobradar.match.profile.ProfileClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily email with matches that were not sent instantly (below alert threshold or in quiet hours). */
@Slf4j
@Component
@RequiredArgsConstructor
public class DigestJob {
    private static final int MAX_PER_EMAIL = 20;

    private final MatchRepository matches;
    private final ProfileClient profiles;
    private final Notifier notifier;

    @Scheduled(cron = "${app.digest-cron}", zone = "${app.zone}")
    public void sendDigests() {
        List<Match> pending = matches.pendingDigest(Instant.now().minus(Duration.ofHours(36)));
        Map<Long, List<Match>> byUser = pending.stream()
                .collect(Collectors.groupingBy(Match::getUserId, LinkedHashMap::new, Collectors.toList()));
        int emails = 0;
        for (var entry : byUser.entrySet()) {
            MatchingProfile user = profiles.byUser(entry.getKey());
            if (user == null) continue;
            List<Match> top = entry.getValue().stream().limit(MAX_PER_EMAIL).toList();
            if (notifier.sendDigest(user, top)) {
                entry.getValue().forEach(m -> m.setDigestSent(true)); // the rest are dropped from future digests too
                matches.saveAll(entry.getValue());
                emails++;
            }
        }
        log.info("Digest: {} emails, {} matches", emails, pending.size());
    }
}
