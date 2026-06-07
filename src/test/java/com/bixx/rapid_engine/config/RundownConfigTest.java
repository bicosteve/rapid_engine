package com.bixx.rapid_engine.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RundownConfig}.
 *
 * <p>Spring Boot binds {@code app.rundown-api.*} to this POJO. The test
 * guards the setter/getter contract used elsewhere by the application.
 */
class RundownConfigTest {

    private RundownConfig config;

    @BeforeEach
    void setUp() {
        config = new RundownConfig();
    }

    @Test
    @DisplayName("defaults: every field is null until set")
    void defaults_areNull() {
        assertThat(config.getKey()).isNull();
        assertThat(config.getHost()).isNull();
        assertThat(config.getAffiliateId()).isNull();
        assertThat(config.getSportsId()).isNull();
    }

    @Test
    @DisplayName("setter/getter: key, host, affiliateId round-trip")
    void basicFields_roundTrip() {
        config.setKey("k1");
        config.setHost("https://api.example.com");
        config.setAffiliateId("23");

        assertThat(config.getKey()).isEqualTo("k1");
        assertThat(config.getHost()).isEqualTo("https://api.example.com");
        assertThat(config.getAffiliateId()).isEqualTo("23");
    }

    @Test
    @DisplayName("setter/getter: sportsId list round-trips")
    void sportsId_listRoundTrips() {
        List<Integer> sports = List.of(1, 2, 3);
        config.setSportsId(sports);

        assertThat(config.getSportsId()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sportsId: empty list is allowed")
    void sportsId_emptyListAllowed() {
        config.setSportsId(List.of());
        assertThat(config.getSportsId()).isEmpty();
    }
}
