package com.jobradar.match.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobradar.match.match.Match;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplatesTest {

    private static SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Match match(String title, String company) {
        Match m = new Match();
        m.setTitle(title);
        m.setCompany(company);
        m.setLocation("Bengaluru");
        m.setUrl("https://jobs.example/1");
        m.setFinalScore(82);
        m.setReasons(List.of("Matches 5 of 6 skills"));
        m.setSkillGaps(List.of("Kubernetes"));
        return m;
    }

    @Test
    void rendersEveryCardWithLinks() {
        Context ctx = new Context();
        ctx.setVariable("name", "Demo");
        ctx.setVariable("frontendUrl", "http://localhost:3000");
        ctx.setVariable("cards", List.of(
                new Notifier.Card(match("Java Developer", "Acme"), "http://save/1", "http://ignore/1"),
                new Notifier.Card(match("React Engineer", "Globex"), "http://save/2", "http://ignore/2")));

        for (String template : List.of("instant", "digest")) {
            String html = engine().process(template, ctx);
            assertThat(html).contains("Java Developer", "Globex", "82%", "Skill gaps: Kubernetes", "http://ignore/2");
        }
    }
}
