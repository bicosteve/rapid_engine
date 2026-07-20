package com.bixx.rapid_engine.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingPropertiesTest {

private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
.withUserConfiguration(PropertiesConfiguration.class);

@Test
void bindsLowercaseKafkaToBrokerEnum() {
contextRunner.withPropertyValues("app.messaging.broker=kafka")
.run(context -> assertThat(context.getBean(MessagingProperties.class).getBroker())
.isEqualTo(MessagingProperties.Broker.KAFKA));
}

@Test
void failsWhenBrokerIsMissing() {
contextRunner.run(context -> assertThat(context.getStartupFailure())
.hasStackTraceContaining("app.messaging.broker"));
}

@Test
void failsWhenBrokerIsInvalid() {
contextRunner.withPropertyValues("app.messaging.broker=redis")
.run(context -> assertThat(context.getStartupFailure())
.hasStackTraceContaining("app.messaging.broker"));
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
static class PropertiesConfiguration {
}
}
