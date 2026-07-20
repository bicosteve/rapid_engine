package com.bixx.rapid_engine.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

@Test
void topicDefaultsUseOnePartitionAndReplica() {
KafkaConfig.TopicDefaults defaults = new KafkaConfig.TopicDefaults();

assertThat(defaults.getPartitions()).isEqualTo(1);
assertThat(defaults.getReplicationFactor()).isEqualTo((short) 1);
}
}
