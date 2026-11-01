package com.jobradar.match.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String coreApiUrl,
        String publicUrl,
        String frontendUrl,
        String mailFrom,
        ZoneId zone,
        Scoring scoring,
        Ai ai) {

    public record Scoring(int prefilter, int minStore, int llmTopN) {}

    public record Ai(String provider, String geminiApiKey, String geminiModel, String geminiEmbeddingModel,
                     String groqApiKey, String groqModel) {}
}
