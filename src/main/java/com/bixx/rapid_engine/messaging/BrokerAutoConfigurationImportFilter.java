package com.bixx.rapid_engine.messaging;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

public class BrokerAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private Environment environment;

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        String broker = environment.getProperty("app.messaging.broker");
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfiguration = autoConfigurationClasses[index];
            matches[index] = !RabbitAutoConfiguration.class.getName().equals(autoConfiguration)
                || "rabbitmq".equalsIgnoreCase(broker);
            if (KafkaAutoConfiguration.class.getName().equals(autoConfiguration)) {
                matches[index] = "kafka".equalsIgnoreCase(broker);
            }
        }
        return matches;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
