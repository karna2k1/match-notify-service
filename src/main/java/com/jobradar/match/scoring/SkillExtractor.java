package com.jobradar.match.scoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Dictionary-based skill finder (skills.txt). Whole-word matching; all-caps aliases
 * such as REST or SQL are matched case-sensitively to avoid hits on plain English words.
 */
@Component
public class SkillExtractor {
    private record Skill(String name, List<Pattern> patterns) {}

    private final List<Skill> skills;

    public SkillExtractor() {
        this(readDictionary());
    }

    SkillExtractor(List<String> lines) {
        this.skills = lines.stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .map(SkillExtractor::toSkill)
                .toList();
    }

    public List<String> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> found = new ArrayList<>();
        for (Skill s : skills) {
            if (s.patterns().stream().anyMatch(p -> p.matcher(text).find())) found.add(s.name());
        }
        return found;
    }

    private static Skill toSkill(String line) {
        List<String> aliases = Arrays.stream(line.split("\\|")).map(String::strip).filter(a -> !a.isEmpty()).toList();
        List<Pattern> patterns = aliases.stream().map(SkillExtractor::toPattern).toList();
        return new Skill(aliases.get(0), patterns);
    }

    private static Pattern toPattern(String alias) {
        boolean caseSensitive = alias.equals(alias.toUpperCase()) && alias.chars().anyMatch(Character::isLetter);
        String regex = "(?<![\\p{Alnum}+#.])" + Pattern.quote(alias) + "(?![\\p{Alnum}+#])";
        return caseSensitive ? Pattern.compile(regex) : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static List<String> readDictionary() {
        try (InputStream in = new ClassPathResource("skills.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
