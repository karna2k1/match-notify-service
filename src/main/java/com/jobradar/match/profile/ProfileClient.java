package com.jobradar.match.profile;

import com.jobradar.match.config.AppProperties;
import com.jobradar.match.config.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Fetches matching profiles from core-api with a service token; cached in memory for 5 minutes. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileClient {
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RestClient http;
    private final Tokens tokens;
    private final AppProperties props;

    private volatile List<MatchingProfile> cached = List.of();
    private volatile Instant cachedAt = Instant.EPOCH;

    public List<MatchingProfile> profiles() {
        if (Instant.now().isBefore(cachedAt.plus(TTL))) return cached;
        try {
            List<MatchingProfile> fresh = http.get()
                    .uri(props.coreApiUrl() + "/internal/profiles")
                    .header("Authorization", "Bearer " + tokens.serviceToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MatchingProfile>>() {});
            cached = fresh == null ? List.of() : fresh;
            cachedAt = Instant.now();
        } catch (RestClientException e) {
            log.warn("core-api profiles unavailable, using {} cached: {}", cached.size(), e.getMessage());
        }
        return cached;
    }

    public MatchingProfile byUser(Long userId) {
        return profiles().stream().filter(p -> p.userId().equals(userId)).findFirst().orElse(null);
    }
}
