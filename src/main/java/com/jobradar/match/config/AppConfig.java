package com.jobradar.match.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {
    public static final String TOPIC_JOB_INGESTED = "job.ingested";
    public static final String TOPIC_JOB_MATCHED = "job.matched";

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(60));
        return builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings)).build();
    }

    @Bean
    NewTopic jobMatchedTopic() {
        return TopicBuilder.name(TOPIC_JOB_MATCHED).partitions(1).replicas(1).build();
    }
}
