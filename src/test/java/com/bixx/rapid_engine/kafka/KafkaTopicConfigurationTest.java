package com.bixx.rapid_engine.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigurationTest {

private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
.withUserConfiguration(KafkaTopicConfiguration.class);

@Test
void createsMatchesTopicFromConfiguredNameAndDefaults() {
KafkaConfig config = configuredKafkaConfig();

NewTopic topic = new KafkaTopicConfiguration(config).matchesTopic();

assertThat(topic.name()).isEqualTo("matches.events");
assertThat(topic.numPartitions()).isEqualTo(1);
assertThat(topic.replicationFactor()).isEqualTo((short) 1);
}

@Test
void createsResultsTopicFromConfiguredNameAndDefaults() {
KafkaConfig config = configuredKafkaConfig();

NewTopic topic = new KafkaTopicConfiguration(config).resultsTopic();

assertThat(topic.name()).isEqualTo("results.events");
assertThat(topic.numPartitions()).isEqualTo(1);
assertThat(topic.replicationFactor()).isEqualTo((short) 1);
}

@Test
void topicDeclarationsAreInactiveWhenRabbitIsSelected() {
contextRunner.withPropertyValues("app.messaging.broker=rabbit")
.run(context -> assertThat(context).doesNotHaveBean(KafkaTopicConfiguration.class));
}

private KafkaConfig configuredKafkaConfig() {
KafkaConfig config = new KafkaConfig();
config.setMatches(topic("matches.events"));
config.setResults(topic("results.events"));
return config;
}

private KafkaConfig.TopicConfig topic(String name) {
KafkaConfig.TopicConfig topic = new KafkaConfig.TopicConfig();
topic.setTopic(name);
return topic;
}
}
