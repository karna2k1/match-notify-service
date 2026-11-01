package com.jobradar.match.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.match.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Picks the AiClient from app.ai.provider: gemini (LLM + embeddings), groq (LLM only) or none. */
@Slf4j
@Configuration
public class AiClientConfig {

    @Bean
    AiClient aiClient(AppProperties props, RestClient http, ObjectMapper mapper) {
        var ai = props.ai();
        String provider = ai.provider() == null ? "none" : ai.provider().toLowerCase();
        if (provider.equals("gemini") && notBlank(ai.geminiApiKey())) {
            log.info("AI provider: Gemini ({}, embeddings {})", ai.geminiModel(), ai.geminiEmbeddingModel());
            return new GeminiClient(http, mapper, ai);
        }
        if (provider.equals("groq") && notBlank(ai.groqApiKey())) {
            log.info("AI provider: Groq ({}), embeddings disabled", ai.groqModel());
            return new GroqClient(http, mapper, ai);
        }
        log.info("AI provider: none (keyword scoring only)");
        return new NoAi();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static Optional<AiClient.Verdict> parseVerdict(ObjectMapper mapper, String json) {
        try {
            String cleaned = json.strip();
            if (cleaned.startsWith("```")) cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").strip();
            JsonNode n = mapper.readTree(cleaned);
            int score = Math.max(0, Math.min(100, n.path("score").asInt(-1)));
            if (n.path("score").isMissingNode()) return Optional.empty();
            List<String> reasons = new ArrayList<>();
            n.path("reasons").forEach(r -> reasons.add(r.asText()));
            List<String> gaps = new ArrayList<>();
            n.path("skillGaps").forEach(g -> gaps.add(g.asText()));
            return Optional.of(new AiClient.Verdict(score, reasons.stream().limit(3).toList(), gaps.stream().limit(5).toList()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static final class NoAi implements AiClient {
        public boolean llmEnabled() { return false; }
        public boolean embeddingsEnabled() { return false; }
        public Optional<Verdict> score(String p, String j) { return Optional.empty(); }
        public List<float[]> embed(List<String> texts) { return List.of(); }
    }

    static final class GeminiClient implements AiClient {
        private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
        private final RestClient http;
        private final ObjectMapper mapper;
        private final AppProperties.Ai cfg;

        GeminiClient(RestClient http, ObjectMapper mapper, AppProperties.Ai cfg) {
            this.http = http;
            this.mapper = mapper;
            this.cfg = cfg;
        }

        public boolean llmEnabled() { return true; }
        public boolean embeddingsEnabled() { return true; }

        // free tier allows ~15 requests/minute: pace calls and back off for a minute after a 429
        private static final long MIN_GAP_MS = 4_500;
        private long lastCallMs;
        private long cooldownUntilMs;

        private synchronized boolean acquire() {
            long now = System.currentTimeMillis();
            if (now < cooldownUntilMs) return false;
            long wait = lastCallMs + MIN_GAP_MS - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            lastCallMs = System.currentTimeMillis();
            return true;
        }

        private synchronized void backOff() {
            cooldownUntilMs = System.currentTimeMillis() + 60_000;
        }

        public Optional<Verdict> score(String profileSummary, String jobText) {
            if (!acquire()) return Optional.empty(); // cooling down: caller falls back to keyword/embedding score
            try {
                var body = Map.of(
                        "contents", List.of(Map.of("parts", List.of(Map.of("text", AiClient.prompt(profileSummary, jobText))))),
                        "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.2));
                JsonNode res = http.post().uri(BASE + cfg.geminiModel() + ":generateContent")
                        .header("x-goog-api-key", cfg.geminiApiKey())
                        .body(body).retrieve().body(JsonNode.class);
                String text = res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
                return parseVerdict(mapper, text);
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                backOff();
                log.warn("Gemini rate limit hit, pausing LLM scoring for 60s (keyword/embedding scores still apply)");
                return Optional.empty();
            } catch (Exception e) {
                log.warn("Gemini score failed: {}", e.getMessage());
                return Optional.empty();
            }
        }

        public List<float[]> embed(List<String> texts) {
            if (texts.isEmpty()) return List.of();
            try {
                String model = "models/" + cfg.geminiEmbeddingModel();
                List<Map<String, Object>> requests = texts.stream()
                        .map(t -> Map.<String, Object>of("model", model,
                                "content", Map.of("parts", List.of(Map.of("text", t.length() > 8000 ? t.substring(0, 8000) : t))),
                                "outputDimensionality", 768))
                        .toList();
                JsonNode res = http.post().uri(BASE + cfg.geminiEmbeddingModel() + ":batchEmbedContents")
                        .header("x-goog-api-key", cfg.geminiApiKey())
                        .body(Map.of("requests", requests)).retrieve().body(JsonNode.class);
                List<float[]> out = new ArrayList<>();
                for (JsonNode e : res.path("embeddings")) {
                    JsonNode values = e.path("values");
                    float[] v = new float[values.size()];
                    for (int i = 0; i < v.length; i++) v[i] = (float) values.get(i).asDouble();
                    out.add(v);
                }
                return out.size() == texts.size() ? out : List.of();
            } catch (Exception e) {
                log.warn("Gemini embeddings failed: {}", e.getMessage());
                return List.of();
            }
        }
    }

    static final class GroqClient implements AiClient {
        private final RestClient http;
        private final ObjectMapper mapper;
        private final AppProperties.Ai cfg;

        GroqClient(RestClient http, ObjectMapper mapper, AppProperties.Ai cfg) {
            this.http = http;
            this.mapper = mapper;
            this.cfg = cfg;
        }

        public boolean llmEnabled() { return true; }
        public boolean embeddingsEnabled() { return false; }

        public Optional<Verdict> score(String profileSummary, String jobText) {
            try {
                var body = Map.of(
                        "model", cfg.groqModel(),
                        "temperature", 0.2,
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(Map.of("role", "user", "content", AiClient.prompt(profileSummary, jobText))));
                JsonNode res = http.post().uri("https://api.groq.com/openai/v1/chat/completions")
                        .header("Authorization", "Bearer " + cfg.groqApiKey())
                        .body(body).retrieve().body(JsonNode.class);
                return parseVerdict(mapper, res.path("choices").path(0).path("message").path("content").asText(""));
            } catch (Exception e) {
                log.warn("Groq score failed: {}", e.getMessage());
                return Optional.empty();
            }
        }

        public List<float[]> embed(List<String> texts) { return List.of(); }
    }
}
