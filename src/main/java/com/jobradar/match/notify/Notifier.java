package com.jobradar.match.notify;

import com.jobradar.match.config.AppProperties;
import com.jobradar.match.config.Tokens;
import com.jobradar.match.match.Match;
import com.jobradar.match.profile.MatchingProfile;
import jakarta.mail.MessagingException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** HTML emails (Thymeleaf): instant alert and daily digest, each card with signed Save/Ignore links. */
@Slf4j
@Component
@RequiredArgsConstructor
public class Notifier {
    private final JavaMailSender mail;
    private final TemplateEngine templates;
    private final Tokens tokens;
    private final AppProperties props;

    public record Card(Match match, String saveUrl, String ignoreUrl) {}

    public boolean sendInstant(MatchingProfile user, Match m) {
        return send(user, "instant", m.getFinalScore() + "% match: " + m.getTitle() + " at " + m.getCompany(), List.of(card(m)));
    }

    public boolean sendDigest(MatchingProfile user, List<Match> matches) {
        return send(user, "digest", "Your JobRadar digest: " + matches.size() + " new matches",
                matches.stream().map(this::card).toList());
    }

    private Card card(Match m) {
        String base = props.publicUrl() + "/feedback?token=";
        return new Card(m, base + tokens.feedbackToken(m.getId(), "SAVED"), base + tokens.feedbackToken(m.getId(), "IGNORED"));
    }

    private boolean send(MatchingProfile user, String template, String subject, List<Card> cards) {
        Context ctx = new Context();
        ctx.setVariable("name", user.fullName());
        ctx.setVariable("cards", cards);
        ctx.setVariable("frontendUrl", props.frontendUrl());
        try {
            var msg = mail.createMimeMessage();
            var helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(props.mailFrom());
            helper.setTo(user.email());
            helper.setSubject(subject);
            helper.setText(templates.process(template, ctx), true);
            mail.send(msg);
            return true;
        } catch (MessagingException | RuntimeException e) { // mail or template failure must never break matching
            log.warn("{} email to user {} failed: {}", template, user.userId(), e.getMessage());
            return false;
        }
    }

    /** Quiet hours may wrap midnight (e.g. 22:00-07:00). */
    public static boolean inQuietHours(MatchingProfile.Profile p, ZoneId zone) {
        return inQuietHours(p.quietHoursStart(), p.quietHoursEnd(), LocalTime.now(zone));
    }

    static boolean inQuietHours(LocalTime start, LocalTime end, LocalTime now) {
        if (start == null || end == null || start.equals(end)) return false;
        return start.isBefore(end)
                ? !now.isBefore(start) && now.isBefore(end)
                : !now.isBefore(start) || now.isBefore(end);
    }
}
