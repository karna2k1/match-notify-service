package com.jobradar.match.match;

import com.jobradar.match.ai.AiClient;
import com.jobradar.match.ai.EmbeddingStore;
import com.jobradar.match.ai.VerdictCache;
import com.jobradar.match.config.AppProperties;
import com.jobradar.match.notify.Notifier;
import com.jobradar.match.profile.MatchingProfile;
import com.jobradar.match.profile.ProfileClient;
import com.jobradar.match.scoring.IngestedJob;
import com.jobradar.match.scoring.KeywordScorer;
import com.jobradar.match.scoring.LocationRules;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Two-stage matching per batch of new jobs:
 * 1) keyword score for every (user, job); 2) for candidates above the prefilter: embedding similarity (pgvector)
 * and an LLM verdict for the user's top N only. Falls back to whatever stages are available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
    private final ProfileClient profileClient;
    private final KeywordScorer keywordScorer;
    private final AiClient ai;
    private final EmbeddingStore embeddings;
    private final VerdictCache verdictCache;
    private final MatchRepository matches;
    private final MatchPublisher publisher;
    private final Notifier notifier;
    private final AppProperties props;

    private static final class Candidate {
        final IngestedJob job;
        final KeywordScorer.Result keyword;
        Integer embedding;
        AiClient.Verdict verdict;
        int pre;

        Candidate(IngestedJob job, KeywordScorer.Result keyword) {
            this.job = job;
            this.keyword = keyword;
            this.pre = keyword.score();
        }
    }

    public void process(List<IngestedJob> jobs) {
        List<MatchingProfile> profiles = profileClient.profiles();
        if (jobs.isEmpty() || profiles.isEmpty()) return;
        Set<Long> embeddedJobs = new HashSet<>();
        int saved = 0;
        for (MatchingProfile user : profiles) {
            try {
                saved += processUser(user, jobs, embeddedJobs);
            } catch (RuntimeException e) {
                log.warn("Matching failed for user {}: {}", user.userId(), e.getMessage());
            }
        }
        log.info("Batch of {} jobs x {} users -> {} matches", jobs.size(), profiles.size(), saved);
    }

    private int processUser(MatchingProfile user, List<IngestedJob> jobs, Set<Long> embeddedJobs) {
        var p = user.profile();
        var s = props.scoring();
        Set<String> ignored = new HashSet<>(matches.repeatedlyIgnoredCompanies(user.userId()));

        List<Candidate> candidates = new ArrayList<>();
        for (IngestedJob job : jobs) {
            if (job.id() == null || blacklisted(p.blacklistCompanies(), job.company())) continue;
            if (LocationRules.fit(p, job) == LocationRules.Fit.MISMATCH) continue; // hard filter: wrong country/city
            if (matches.existsByUserIdAndJobId(user.userId(), job.id())) continue;
            var kw = keywordScorer.score(p, job, ignored.contains(lower(job.company())));
            if (kw.score() >= s.prefilter()) candidates.add(new Candidate(job, kw));
        }
        if (candidates.isEmpty()) return 0;

        if (ai.embeddingsEnabled()) applyEmbeddings(user, candidates, embeddedJobs);

        if (ai.llmEnabled()) {
            String profileHash = sha256(p.summary());
            candidates.stream().sorted(Comparator.comparingInt((Candidate c) -> c.pre).reversed())
                    .limit(s.llmTopN())
                    .forEach(c -> {
                        String key = "llm:" + user.userId() + ":" + c.job.id() + ":" + profileHash.substring(0, 12);
                        c.verdict = verdictCache.get(key).orElseGet(() -> ai.score(p.summary(), c.job.text())
                                .map(v -> { verdictCache.put(key, v); return v; })
                                .orElse(null));
                    });
        }

        int saved = 0;
        for (Candidate c : candidates) {
            int finalScore = c.verdict == null ? c.pre : Math.round(0.5f * c.pre + 0.5f * c.verdict.score());
            if (finalScore < s.minStore()) continue;
            Match m = toMatch(user.userId(), c, finalScore);
            try {
                matches.saveAndFlush(m);
            } catch (DataIntegrityViolationException duplicate) {
                continue;
            }
            saved++;
            if (finalScore >= p.minMatchScore()) {
                publisher.publish(m, "MATCHED");
                if (!Notifier.inQuietHours(p, props.zone()) && notifier.sendInstant(user, m)) {
                    m.setInstantSent(true);
                    matches.save(m);
                }
            }
        }
        return saved;
    }

    private void applyEmbeddings(MatchingProfile user, List<Candidate> candidates, Set<Long> embeddedJobs) {
        String summary = user.profile().summary();
        String hash = sha256(summary);
        if (!embeddings.profileHash(user.userId()).map(hash::equals).orElse(false)) {
            List<float[]> v = ai.embed(List.of(summary));
            if (v.isEmpty()) return;
            embeddings.saveProfile(user.userId(), hash, v.get(0));
        }
        List<IngestedJob> missing = candidates.stream().map(c -> c.job)
                .filter(j -> !embeddedJobs.contains(j.id()) && !embeddings.hasJob(j.id())).toList();
        for (int i = 0; i < missing.size(); i += 100) {
            List<IngestedJob> chunk = missing.subList(i, Math.min(i + 100, missing.size()));
            List<float[]> vectors = ai.embed(chunk.stream().map(IngestedJob::text).toList());
            for (int k = 0; k < vectors.size(); k++) {
                embeddings.saveJob(chunk.get(k).id(), vectors.get(k));
                embeddedJobs.add(chunk.get(k).id());
            }
        }
        Map<Long, Double> sims = embeddings.similarities(user.userId(), candidates.stream().map(c -> c.job.id()).toList());
        for (Candidate c : candidates) {
            Double sim = sims.get(c.job.id());
            if (sim == null) continue;
            // cosine similarity of related texts sits roughly in 0.55-0.90; stretch it to 0-100
            c.embedding = (int) Math.max(0, Math.min(100, Math.round((sim - 0.55) / 0.35 * 100)));
            c.pre = Math.round(0.6f * c.keyword.score() + 0.4f * c.embedding);
        }
    }

    private Match toMatch(Long userId, Candidate c, int finalScore) {
        IngestedJob j = c.job;
        Match m = new Match();
        m.setUserId(userId);
        m.setJobId(j.id());
        m.setSource(clip(j.source(), 40));
        m.setCompany(clip(j.company() == null ? "Unknown" : j.company(), 200));
        m.setTitle(clip(j.title(), 300));
        m.setLocation(clip(j.location(), 300));
        m.setRemote(j.remote());
        m.setUrl(clip(j.url(), 1000));
        m.setKeywordScore(c.keyword.score());
        m.setEmbeddingScore(c.embedding);
        m.setLlmScore(c.verdict == null ? null : c.verdict.score());
        m.setFinalScore(finalScore);
        Set<String> reasons = new LinkedHashSet<>(c.keyword.reasons());
        if (c.verdict != null) reasons.addAll(c.verdict.reasons());
        m.setReasons(reasons.stream().limit(5).toList());
        m.setMatchedSkills(c.keyword.matchedSkills());
        m.setSkillGaps(c.verdict != null && !c.verdict.skillGaps().isEmpty() ? c.verdict.skillGaps() : c.keyword.missingSkills());
        return m;
    }

    static boolean blacklisted(List<String> blacklist, String company) {
        String c = lower(company);
        return !c.isEmpty() && blacklist.stream().anyMatch(b -> !b.isBlank() && c.contains(lower(b)));
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).strip();
    }

    private static String clip(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    private static String sha256(String v) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
