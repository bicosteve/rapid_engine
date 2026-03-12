package com.bixx.rapid_engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
@ConfigurationProperties(prefix = "app.rundown-api")
public class RundownConfig {
    private String key;
    private String host;
    private List<Integer> sportsId; // springs will auto split comma separated list
}
