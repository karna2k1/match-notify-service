package com.jobradar.match.match;

import com.jobradar.match.config.Tokens;
import com.jobradar.match.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequiredArgsConstructor
public class MatchController {
    private final MatchRepository matches;
    private final MatchPublisher publisher;
    private final Tokens tokens;
    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record StatusRequest(@NotNull Match.Status status) {}

    @GetMapping("/matches")
    public PageResponse<MatchDto> list(@AuthenticationPrincipal Long userId,
                                       @RequestParam(defaultValue = "0") int minScore,
                                       @RequestParam(defaultValue = "") String status,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String st = status.isBlank() || status.equalsIgnoreCase("ALL") ? "" : status.toUpperCase();
        var p = matches.search(userId, minScore, st, q.strip(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return new PageResponse<>(p.getContent().stream().map(MatchDto::from).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @PatchMapping("/matches/{id}")
    @Transactional
    public MatchDto setStatus(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                              @Valid @RequestBody StatusRequest req) {
        Match m = matches.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        return MatchDto.from(applyStatus(m, req.status()));
    }

    /** Target of the Save/Ignore links in emails; the signed token replaces login. */
    @GetMapping(value = "/feedback", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public String feedback(@RequestParam String token) {
        Claims c;
        try {
            c = tokens.parse(token);
        } catch (JwtException e) {
            return page("This link has expired or is invalid.");
        }
        Long matchId = c.get("mid", Long.class);
        Match.Status status = Match.Status.valueOf(c.get("act", String.class));
        return matches.findById(matchId)
                .map(m -> {
                    applyStatus(m, status);
                    return page((status == Match.Status.SAVED ? "Saved: " : "Ignored: ")
                            + HtmlUtils.htmlEscape(m.getTitle() + " at " + m.getCompany()));
                })
                .orElseGet(() -> page("Match not found."));
    }

    /** Score distribution, status counts and most frequent skill gaps, for insights-mfe. */
    @GetMapping("/stats/matches")
    public Map<String, Object> stats(@AuthenticationPrincipal Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scoreDistribution", jdbc.queryForList(
                "select (final_score / 10) * 10 as bucket, count(*) as count from matches where user_id = ? group by bucket order by bucket", userId));
        out.put("byStatus", jdbc.queryForList(
                "select status, count(*) as count from matches where user_id = ? group by status", userId));
        out.put("topSkillGaps", jdbc.queryForList(
                "select gap as skill, count(*) as count from matches m, jsonb_array_elements_text(m.skill_gaps) gap "
                        + "where m.user_id = ? and m.status <> 'IGNORED' group by gap order by count(*) desc limit 10", userId));
        out.put("bySource", jdbc.queryForList(
                "select coalesce(source, 'UNKNOWN') as source, count(*) as count, round(avg(final_score)) as avgScore "
                        + "from matches where user_id = ? group by source order by count(*) desc", userId));
        return out;
    }

    private Match applyStatus(Match m, Match.Status status) {
        if (m.getStatus() != status) {
            m.setStatus(status);
            matches.save(m);
            if (status != Match.Status.NEW) publisher.publish(m, status.name());
        }
        return m;
    }

    private String page(String message) {
        return """
                <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>JobRadar</title></head>
                <body style="font-family:system-ui,sans-serif;max-width:480px;margin:64px auto;padding:0 16px;color:#1f2937">
                <h2 style="color:#4f46e5">JobRadar</h2><p>%s</p>
                <p><a href="%s" style="color:#4f46e5">Open JobRadar</a></p></body></html>
                """.formatted(message, props.frontendUrl());
    }
}
