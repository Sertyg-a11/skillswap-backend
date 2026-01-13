package nl.ak.skillswap.messageservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingAmqpConfig {

    @Value("${app.messaging.exchange}")
    private String exchangeName;

    @Bean
    TopicExchange skillswapExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue messageCreatedQueue() {
        return QueueBuilder.durable("message-service.message-created").build();
    }

    @Bean
    Binding messageCreatedBinding(TopicExchange skillswapExchange, Queue messageCreatedQueue) {
        return BindingBuilder.bind(messageCreatedQueue)
                .to(skillswapExchange)
                .with("message.created");
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
