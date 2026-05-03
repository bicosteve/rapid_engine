package com.bixx.rapid_engine.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        return new Queue(this.rabbitMQConfig.getMatches().getQueue());
    }

    // 02. Declare the exchange
    @Bean
    public TopicExchange matchesExchange(){
        return new TopicExchange(this.rabbitMQConfig.getMatches().getExchange());
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
                .with(this.rabbitMQConfig.getMatches().getRoutingKey());
    }

    // 04. Declare resultsQueue
    @Bean
    public Queue resultsQueue(){
        return new Queue(this.rabbitMQConfig.getResults().getQueue());
    }


    // 05. Declare resultsExchange
    @Bean
    public TopicExchange resultsExchange(){
        return new TopicExchange(this.rabbitMQConfig.getResults().getExchange());
    }

    // 06. Bind the resultsQueue and resultsExchange
    @Bean
    public Binding resultsBinding(Queue resultsQueue, TopicExchange resultsExchange){
        return BindingBuilder
                .bind(resultsQueue)
                .to(resultsExchange)
                .with(this.rabbitMQConfig.getResults().getRoutingKey());
    }


    // 07. Jackson converter - used to serailize/deserialize messages as JSON
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper){
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // 08. RabbitTemplate - used by producer to send messages
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
