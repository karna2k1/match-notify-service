package com.jobradar.match.match;

import java.time.Instant;
import java.util.List;

public record MatchDto(Long id, Long jobId, String source, String company, String title, String location,
                       boolean remote, String url, int finalScore, int keywordScore, Integer embeddingScore,
                       Integer llmScore, List<String> reasons, List<String> matchedSkills, List<String> skillGaps,
                       Match.Status status, Instant createdAt) {

    public static MatchDto from(Match m) {
        return new MatchDto(m.getId(), m.getJobId(), m.getSource(), m.getCompany(), m.getTitle(), m.getLocation(),
                m.isRemote(), m.getUrl(), m.getFinalScore(), m.getKeywordScore(), m.getEmbeddingScore(),
                m.getLlmScore(), m.getReasons(), m.getMatchedSkills(), m.getSkillGaps(), m.getStatus(), m.getCreatedAt());
    }
}
