package com.jobradar.match.profile;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** Mirror of core-api's /internal/profiles response. */
public record MatchingProfile(Long userId, String email, String fullName, Profile profile) {

    public record Profile(String headline, BigDecimal experienceYears, BigDecimal currentCtcLpa,
                          BigDecimal expectedCtcLpa, Integer noticePeriodDays, String workMode, List<String> skills,
                          List<String> targetRoles, List<String> locations, List<String> blacklistCompanies,
                          int minMatchScore, LocalTime quietHoursStart, LocalTime quietHoursEnd) {

        public List<String> skills() {
            return skills == null ? List.of() : skills;
        }

        public List<String> targetRoles() {
            return targetRoles == null ? List.of() : targetRoles;
        }

        public List<String> locations() {
            return locations == null ? List.of() : locations;
        }

        public List<String> blacklistCompanies() {
            return blacklistCompanies == null ? List.of() : blacklistCompanies;
        }

        /** Text used for embeddings and the LLM prompt. */
        public String summary() {
            return "Headline: " + (headline == null ? "" : headline)
                    + "\nExperience (years): " + (experienceYears == null ? "unknown" : experienceYears)
                    + "\nSkills: " + String.join(", ", skills())
                    + "\nTarget roles: " + String.join(", ", targetRoles())
                    + "\nPreferred locations: " + String.join(", ", locations())
                    + "\nWork mode: " + workMode;
        }
    }
}
