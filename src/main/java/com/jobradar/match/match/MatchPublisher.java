package com.jobradar.match.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.match.config.AppConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes job.matched; core-api turns these into kanban cards (MATCHED -> Suggested, SAVED, IGNORED). */
@Slf4j
@Component
public class MatchPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public MatchPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          @Value("${app.kafka.enabled}") boolean enabled) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public void publish(Match m, String status) {
        if (!enabled) return;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userId", m.getUserId());
        event.put("jobId", String.valueOf(m.getJobId()));
        event.put("company", m.getCompany());
        event.put("title", m.getTitle());
        event.put("location", m.getLocation());
        event.put("url", m.getUrl());
        event.put("source", m.getSource());
        event.put("score", m.getFinalScore());
        event.put("status", status);
        try {
            kafka.send(AppConfig.TOPIC_JOB_MATCHED, String.valueOf(m.getUserId()), mapper.writeValueAsString(event))
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.warn("job.matched send failed for match {}: {}", m.getId(), ex.getMessage());
                    });
        } catch (Exception e) {
            log.warn("job.matched publish failed for match {}: {}", m.getId(), e.getMessage());
        }
    }
}
