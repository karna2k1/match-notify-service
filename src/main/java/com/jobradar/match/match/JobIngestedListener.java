package com.jobradar.match.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.match.scoring.IngestedJob;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Batch consumer for job.ingested (up to 50 per poll), so LLM top-N is chosen across the batch. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobIngestedListener {
    private final ObjectMapper mapper;
    private final MatchService service;

    @KafkaListener(topics = "job.ingested", autoStartup = "${app.kafka.enabled}")
    public void onBatch(List<String> payloads) {
        List<IngestedJob> jobs = new ArrayList<>();
        for (String p : payloads) {
            try {
                jobs.add(mapper.readValue(p, IngestedJob.class));
            } catch (Exception e) {
                log.warn("Skipping bad job.ingested message: {}", e.getMessage());
            }
        }
        service.process(jobs);
    }
}
