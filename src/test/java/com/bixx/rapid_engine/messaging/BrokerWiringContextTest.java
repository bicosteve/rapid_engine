package com.bixx.rapid_engine.messaging;

import com.bixx.rapid_engine.Main;
import com.bixx.rapid_engine.kafka.KafkaConfig;
import com.bixx.rapid_engine.kafka.KafkaEventPublisher;
import com.bixx.rapid_engine.kafka.KafkaTopicConfiguration;
import com.bixx.rapid_engine.rabbitmq.RabbitEventPublisher;
import com.bixx.rapid_engine.rabbitmq.RabbitMQBeans;
import com.bixx.rapid_engine.rabbitmq.RabbitMQConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerWiringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Main.class)
            .withPropertyValues(
                    "app.rabbitmq.matches.exchange=matches.exchange",
                    "app.rabbitmq.matches.queue=matches.queue",
                    "app.rabbitmq.matches.routing-key=matches",
                    "app.rabbitmq.results.exchange=results.exchange",
                    "app.rabbitmq.results.queue=results.queue",
                    "app.rabbitmq.results.routing-key=results",
                    "spring.rabbitmq.host=rabbit.example",
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.admin.auto-create=false"
            );

    @Test
    void requiresBrokerSelection(){
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.messaging.broker");
            assertThat(context.getStartupFailure().getMessage())
                    .doesNotContain("No qualifying bean of type 'com.bixx.rapid_engine.messaging.EventPublisher'");
        });
    }

    @Test
    void rejectsInvalidBrokerSelectionBeforePublisherWiring(){
        contextRunner.withPropertyValues("app.messaging.broker=not-a-broker")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("app.messaging.broker")
                            .hasStackTraceContaining("not-a-broker");
                    assertThat(context.getStartupFailure().getMessage())
                            .doesNotContain("No qualifying bean of type 'com.bixx.rapid_engine.messaging.EventPublisher'");
                });
    }


    @Test
    void productionRabbitModeStartsWithoutKafkaProperties(){
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Main.class)
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.messaging.broker=rabbitmq",
                        "spring.rabbitmq.host=rabbit.example",
                        "app.rabbitmq.matches.exchange=matches.exchange",
                        "app.rabbitmq.matches.queue=matches.queue",
                        "app.rabbitmq.matches.routing-key=matches",
                        "app.rabbitmq.results.exchange=results.exchange",
                        "app.rabbitmq.results.queue=results.queue",
"app.rabbitmq.results.routing-key=results",
"spring.data.redis.host=localhost",
"spring.data.redis.username=default",
"spring.data.redis.port=6379",
"spring.data.redis.database=0",
"spring.data.redis.password=dummy-password",
"spring.data.redis.ssl.enabled=false",
"spring.data.redis.timeout=10000",
"app.rundown-api.key=dummy-rundown-key",
"app.rundown-api.host=https://rundown.example",
"app.rundown-api.sports-id=1",
"app.rundown-api.affiliate-id=dummy-affiliate-id",
"app.rundown-api.poll=false",
"app.rundown-api.delay=0"
)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertSingleEventPublisher(context, RabbitEventPublisher.class);
                    assertThat(context).doesNotHaveBean(KafkaConfig.class);
                    assertThat(context).doesNotHaveBean(KafkaTemplate.class);
                });
    }

    @Test
    void productionKafkaModeStartsWithoutRabbitProperties(){
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Main.class)
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.messaging.broker=kafka",
"spring.kafka.bootstrap-servers=localhost:9092",
"spring.kafka.admin.auto-create=false",
"spring.data.redis.host=localhost",
"spring.data.redis.username=default",
"spring.data.redis.port=6379",
"spring.data.redis.database=0",
"spring.data.redis.password=dummy-password",
"spring.data.redis.ssl.enabled=false",
"spring.data.redis.timeout=10000",
"app.rundown-api.key=dummy-rundown-key",
"app.rundown-api.host=https://rundown.example",
"app.rundown-api.sports-id=1",
"app.rundown-api.affiliate-id=dummy-affiliate-id",
"app.rundown-api.poll=false",
"app.rundown-api.delay=0"
)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertSingleEventPublisher(context, KafkaEventPublisher.class);
                    assertThat(context).doesNotHaveBean(RabbitMQConfig.class);
                    assertThat(context).doesNotHaveBean(ConnectionFactory.class);
                });
    }

    @Test
    void rabbitModeRegistersOnlyRabbitCustomAndNativeInfrastructure(){
        contextRunner.withPropertyValues("app.messaging.broker=rabbitmq")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertSingleEventPublisher(context, RabbitEventPublisher.class);
                    assertThat(context).hasSingleBean(RabbitMQConfig.class);
                    assertThat(context).hasSingleBean(RabbitMQBeans.class);
                    assertThat(context).hasSingleBean(ConnectionFactory.class);
                    assertThat(context).hasSingleBean(RabbitTemplate.class);
                    assertThat(context.getBeansOfType(Queue.class)).hasSize(2);
                    assertThat(context).doesNotHaveBean(KafkaConfig.class);
                    assertThat(context).doesNotHaveBean(KafkaTopicConfiguration.class);
                    assertThat(context).doesNotHaveBean(KafkaEventPublisher.class);
                    assertThat(context).doesNotHaveBean(KafkaTemplate.class);
                    assertThat(context).doesNotHaveBean(NewTopic.class);
                });
    }

    @Test
    void kafkaModeRegistersOnlyKafkaCustomAndNativeInfrastructure(){
        contextRunner.withPropertyValues("app.messaging.broker=kafka")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertSingleEventPublisher(context, KafkaEventPublisher.class);
                    assertThat(context).hasSingleBean(KafkaConfig.class);
                    assertThat(context).hasSingleBean(KafkaTopicConfiguration.class);
                    assertThat(context).hasSingleBean(KafkaTemplate.class);
                    assertThat(context.getBeansOfType(NewTopic.class)).hasSize(2);
                    assertThat(context).doesNotHaveBean(RabbitMQConfig.class);
                    assertThat(context).doesNotHaveBean(RabbitMQBeans.class);
                    assertThat(context).doesNotHaveBean(RabbitEventPublisher.class);
                    assertThat(context).doesNotHaveBean(ConnectionFactory.class);
                    assertThat(context).doesNotHaveBean(RabbitTemplate.class);
                    assertThat(context).doesNotHaveBean(Queue.class);
                });
    }

    @Test
    void rabbitModeRejectsBlankRabbitHostBeforeRabbitInfrastructureCreation(){
        NativeInfrastructureProbe.reset();
        contextRunner.withUserConfiguration(NativeInfrastructureProbe.class)
                .withPropertyValues(
                        "app.messaging.broker=rabbitmq",
                        "spring.rabbitmq.host="
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("spring.rabbitmq.host");
                    assertThat(NativeInfrastructureProbe.rabbitInfrastructureCreated).isFalse();
                });
    }

    @Test
    void kafkaModeRejectsBlankKafkaBootstrapServersBeforeKafkaInfrastructureCreation(){
        NativeInfrastructureProbe.reset();
        contextRunner.withUserConfiguration(NativeInfrastructureProbe.class)
                .withPropertyValues(
                        "app.messaging.broker=kafka",
                        "spring.kafka.bootstrap-servers="
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("spring.kafka.bootstrap-servers");
                    assertThat(NativeInfrastructureProbe.kafkaInfrastructureCreated).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class NativeInfrastructureProbe {
        private static final AtomicBoolean rabbitInfrastructureCreated = new AtomicBoolean();
        private static final AtomicBoolean kafkaInfrastructureCreated = new AtomicBoolean();

        static void reset(){
            rabbitInfrastructureCreated.set(false);
            kafkaInfrastructureCreated.set(false);
        }

        @Bean
        static BeanPostProcessor nativeInfrastructureProbe(){
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName){
                    if(bean instanceof ConnectionFactory || bean instanceof RabbitTemplate) {
                        rabbitInfrastructureCreated.set(true);
                    }
                    if(bean instanceof KafkaTemplate) {
                        kafkaInfrastructureCreated.set(true);
                    }
                    return bean;
                }
            };
        }
    }

    private void assertSingleEventPublisher(ApplicationContext context,

                                            Class<? extends EventPublisher> expectedPublisherType){
        assertThat(context.getBeansOfType(EventPublisher.class)).hasSize(1);
        assertThat(context.getBean(EventPublisher.class)).isInstanceOf(expectedPublisherType);
    }
}
