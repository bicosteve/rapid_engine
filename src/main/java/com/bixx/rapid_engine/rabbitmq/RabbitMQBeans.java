package com.bixx.rapid_engine.rabbitmq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMQBeans {
    private final RabbitMQConfig rabbitMQConfig;

    // 01. Declare the queue
    @Bean
    public Queue matchesQueue(){
        return new Queue(this.rabbitMQConfig.getQueue());
    }

    // 02. Declare the exchange
    @Bean
    public TopicExchange matchesExchange(){
        return new TopicExchange(this.rabbitMQConfig.getExchange());
    }

    // 03. Bind the queue to exchange with routing key
    @Bean
    public Binding matchesBinding(
            Queue matchesQueue,
            TopicExchange matchesExchange
    ){
        return BindingBuilder
                .bind(matchesQueue)
                .to(matchesExchange)
                .with(this.rabbitMQConfig.getRoutingKey());
    }

    // 04. Jackson converter - used to serailize/deserialize messages as JSON
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper){
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // 05. RabbitTemplate - used by producer to send messages
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory factory,
            MessageConverter messageConverter
    ){
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(messageConverter);
        return template;
    }

}
