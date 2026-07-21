package com.bixx.rapid_engine.messaging;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class BrokerConnectionValidationConfiguration {

	@Bean
	static BeanFactoryPostProcessor brokerConnectionValidation(Environment environment) {
		return beanFactory -> {
			String configuredBroker = environment.getProperty("app.messaging.broker");
			if (!StringUtils.hasText(configuredBroker)) {
				throw new IllegalStateException("Required property is blank: app.messaging.broker");
			}

			MessagingProperties.Broker broker;
			try {
				broker = MessagingProperties.Broker.valueOf(configuredBroker.toUpperCase());
			} catch (IllegalArgumentException exception) {
				throw new IllegalStateException(
						"Invalid app.messaging.broker value: " + configuredBroker,
						exception);
			}

			switch (broker) {
				case RABBITMQ -> requireText(environment, "spring.rabbitmq.host");
				case KAFKA -> requireText(environment, "spring.kafka.bootstrap-servers");
			}
		};
	}

	private static void requireText(Environment environment, String property) {
		if (!StringUtils.hasText(environment.getProperty(property))) {
			throw new IllegalStateException("Required property is blank: " + property);
		}
	}
}
