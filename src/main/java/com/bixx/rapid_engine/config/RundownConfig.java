package com.bixx.rapid_engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties(prefix = "app.rundown-api")
public class RundownConfig {
    private String apiKey;
    private String baseUrl;
    private String sportsId;

}
