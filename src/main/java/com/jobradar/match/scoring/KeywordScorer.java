package com.jobradar.match.scoring;

import com.jobradar.match.profile.MatchingProfile.Profile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 1, free and fast: skills overlap (55%), title vs target roles (25%), location / work mode (20%),
 * minus penalties for experience gap, seniority and companies the user keeps ignoring.
 */
@Component
@RequiredArgsConstructor
public class KeywordScorer {
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:-|to)?\\s*(?:\\d{1,2})?\\s*(?:years|yrs)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENIOR_TITLE = Pattern.compile("\\b(principal|staff|director|head of|vp|distinguished)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP = Set.of("and", "the", "for", "with", "engineer", "developer");

    private final SkillExtractor skills;

    public record Result(int score, List<String> matchedSkills, List<String> missingSkills, List<String> reasons) {}

    public Result score(Profile p, IngestedJob job, boolean ignoredCompany) {
        List<String> reasons = new ArrayList<>();

        // skills
        List<String> jobSkills = skills.extract(job.title() + "\n" + (job.description() == null ? "" : job.description()));
        Set<String> mine = new HashSet<>();
        p.skills().forEach(s -> mine.add(s.toLowerCase(Locale.ROOT)));
        skills.extract(String.join(", ", p.skills())).forEach(s -> mine.add(s.toLowerCase(Locale.ROOT)));
        List<String> matched = jobSkills.stream().filter(s -> mine.contains(s.toLowerCase(Locale.ROOT))).toList();
        List<String> missing = jobSkills.stream().filter(s -> !mine.contains(s.toLowerCase(Locale.ROOT))).limit(8).toList();
        int skillScore = jobSkills.isEmpty() ? 50
                : (int) Math.min(100, Math.round(100.0 * matched.size() / Math.min(jobSkills.size(), 8)));
        if (!matched.isEmpty()) {
            reasons.add("Matches " + matched.size() + " of " + jobSkills.size() + " skills: "
                    + String.join(", ", matched.stream().limit(5).toList()));
        }

        // title vs target roles
        int roleScore = 60;
        String title = job.title().toLowerCase(Locale.ROOT);
        if (!p.targetRoles().isEmpty()) {
            roleScore = 0;
            for (String role : p.targetRoles()) {
                String r = role.toLowerCase(Locale.ROOT).strip();
                if (title.contains(r)) {
                    roleScore = 100;
                    reasons.add("Title matches target role \"" + role + "\"");
                    break;
                }
                List<String> tokens = Arrays.stream(r.split("[^a-z0-9+#.]+"))
                        .filter(t -> t.length() > 2 && !STOP.contains(t)).toList();
                if (!tokens.isEmpty()) {
                    long hit = tokens.stream().filter(title::contains).count();
                    roleScore = Math.max(roleScore, (int) (70.0 * hit / tokens.size()));
                }
            }
        }

        // location / work mode
        int locScore = switch (LocationRules.fit(p, job)) {
            case IN_PLACE -> {
                reasons.add("In a preferred location (" + job.location() + ")");
                yield 100;
            }
            case OPEN_REMOTE -> {
                reasons.add("Remote");
                yield 100;
            }
            case NO_PREFERENCE -> 70;
            case MISMATCH -> 0;
        };

        double score = 0.55 * skillScore + 0.25 * roleScore + 0.20 * locScore;

        // penalties
        double exp = p.experienceYears() == null ? -1 : p.experienceYears().doubleValue();
        Integer required = requiredYears(job.description());
        if (exp >= 0 && required != null && required > exp + 2) {
            score -= 15;
            reasons.add("Asks for " + required + "+ years (you have " + stripZeros(exp) + ")");
        }
        if (exp >= 0 && exp < 6 && SENIOR_TITLE.matcher(job.title()).find()) score -= 10;
        if (ignoredCompany) score -= 15;

        return new Result((int) Math.max(0, Math.min(100, Math.round(score))), matched, missing, reasons);
    }

    static Integer requiredYears(String description) {
        if (description == null) return null;
        Matcher m = YEARS.matcher(description);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y >= 1 && y <= 20) return y;
        }
        return null;
    }

    private static String stripZeros(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
