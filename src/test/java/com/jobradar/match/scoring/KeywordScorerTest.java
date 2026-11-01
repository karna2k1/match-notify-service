package com.jobradar.match.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobradar.match.profile.MatchingProfile.Profile;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordScorerTest {
    private final KeywordScorer scorer = new KeywordScorer(new SkillExtractor());

    private static Profile profile(String workMode, List<String> locations) {
        return new Profile("Full stack dev", new BigDecimal("3"), null, null, 30, workMode,
                List.of("Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "Docker"),
                List.of("Full Stack Developer"), locations, List.of(), 70, null, null);
    }

    private static IngestedJob job(String title, String location, boolean remote, String description) {
        return new IngestedJob(1L, "GREENHOUSE", "Acme", title, location, remote, "https://x/1", description, null, null);
    }

    @Test
    void strongFitScoresHigh() {
        var r = scorer.score(profile("ANY", List.of("Bengaluru")),
                job("Full Stack Developer", "Bengaluru, India", false,
                        "Java, Spring Boot, React and TypeScript. PostgreSQL, Docker. 2+ years experience."), false);
        assertThat(r.score()).isGreaterThanOrEqualTo(85);
        assertThat(r.matchedSkills()).contains("Java", "Spring Boot", "React");
        assertThat(r.missingSkills()).isEmpty();
    }

    @Test
    void weakFitAndPenaltiesScoreLow() {
        var r = scorer.score(profile("REMOTE", List.of()),
                job("Staff Golang Engineer", "Berlin", false, "Golang, Kubernetes, Terraform. 10+ years."), true);
        assertThat(r.score()).isLessThan(30);
        assertThat(r.missingSkills()).contains("Golang", "Kubernetes", "Terraform");
        assertThat(r.reasons()).anyMatch(s -> s.contains("10+ years"));
    }

    @Test
    void parsesRequiredYears() {
        assertThat(KeywordScorer.requiredYears("We need 5+ years of Java")).isEqualTo(5);
        assertThat(KeywordScorer.requiredYears("3-5 yrs experience")).isEqualTo(3);
        assertThat(KeywordScorer.requiredYears("no requirement")).isNull();
    }
}
