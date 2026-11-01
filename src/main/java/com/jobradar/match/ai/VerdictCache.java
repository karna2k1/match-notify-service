package com.jobradar.match.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis cache for LLM verdicts (7 days) so re-deliveries and replays don't spend free-tier quota. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerdictCache {
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public Optional<AiClient.Verdict> get(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, AiClient.Verdict.class));
        } catch (Exception e) {
            log.debug("verdict cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, AiClient.Verdict verdict) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(verdict), TTL);
        } catch (Exception e) {
            log.debug("verdict cache write failed: {}", e.getMessage());
        }
    }
}
