package com.jobradar.match.ai;

import java.util.List;
import java.util.Optional;

/** Provider-neutral AI access (Gemini, Groq, or none). Callers must work when everything is disabled. */
public interface AiClient {

    record Verdict(int score, List<String> reasons, List<String> skillGaps) {}

    boolean llmEnabled();

    boolean embeddingsEnabled();

    /** LLM fit score 0-100 with reasons and missing skills; empty on any failure. */
    Optional<Verdict> score(String profileSummary, String jobText);

    /** 768-dim embeddings, same order as input; empty list on failure. */
    List<float[]> embed(List<String> texts);

    static String prompt(String profileSummary, String jobText) {
        return """
                You are a recruiter scoring how well a candidate fits a job.
                Reply with JSON only: {"score": <0-100 integer>, "reasons": [<max 3 short strings>], "skillGaps": [<max 5 skills the job needs that the candidate lacks>]}

                CANDIDATE
                %s

                JOB
                %s
                """.formatted(profileSummary, jobText.length() > 6000 ? jobText.substring(0, 6000) : jobText);
    }
}
