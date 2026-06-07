package com.bixx.rapid_engine.rabbitmq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RabbitMQConfig} and its nested {@link RabbitMQConfig.QueueConfig}.
 *
 * <p>Spring Boot binds {@code app.rabbitmq.matches.*} and
 * {@code app.rabbitmq.results.*} to these POJOs. The test ensures the
 * setters and getters are wired correctly.
 */
class RabbitMQConfigTest {

    private RabbitMQConfig config;
    private RabbitMQConfig.QueueConfig matches;
    private RabbitMQConfig.QueueConfig results;

    @BeforeEach
    void setUp() {
        config = new RabbitMQConfig();
        matches = new RabbitMQConfig.QueueConfig();
        results = new RabbitMQConfig.QueueConfig();
    }

    @Test
    @DisplayName("matches: setter/getter round-trip")
    void matches_setterGetterRoundTrip() {
        matches.setExchange("m.ex");
        matches.setQueue("m.q");
        matches.setRoutingKey("m.rk");
        config.setMatches(matches);

        assertThat(config.getMatches()).isSameAs(matches);
        assertThat(config.getMatches().getExchange()).isEqualTo("m.ex");
        assertThat(config.getMatches().getQueue()).isEqualTo("m.q");
        assertThat(config.getMatches().getRoutingKey()).isEqualTo("m.rk");
    }

    @Test
    @DisplayName("results: setter/getter round-trip")
    void results_setterGetterRoundTrip() {
        results.setExchange("r.ex");
        results.setQueue("r.q");
        results.setRoutingKey("r.rk");
        config.setResults(results);

        assertThat(config.getResults()).isSameAs(results);
        assertThat(config.getResults().getExchange()).isEqualTo("r.ex");
        assertThat(config.getResults().getQueue()).isEqualTo("r.q");
        assertThat(config.getResults().getRoutingKey()).isEqualTo("r.rk");
    }

    @Test
    @DisplayName("defaults: matches and results are null until set")
    void defaults_areNull() {
        assertThat(config.getMatches()).isNull();
        assertThat(config.getResults()).isNull();
    }

    @Test
    @DisplayName("QueueConfig: defaults are null until set")
    void queueConfig_defaultsAreNull() {
        RabbitMQConfig.QueueConfig q = new RabbitMQConfig.QueueConfig();
        assertThat(q.getExchange()).isNull();
        assertThat(q.getQueue()).isNull();
        assertThat(q.getRoutingKey()).isNull();
    }
}
