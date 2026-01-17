package nl.ak.skillswap.skillswap.gdpr.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for GDPR orchestration.
 */
@Configuration
public class GdprRabbitConfig {

    @Value("${app.gdpr.exchange:skillswap.events}")
    private String exchange;

    // Queue for receiving aggregated export responses
    public static final String GDPR_EXPORT_RESPONSE_QUEUE = "gdpr.export.response.gateway";

    @Bean
    public TopicExchange gdprExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue gdprExportResponseQueue() {
        return QueueBuilder.durable(GDPR_EXPORT_RESPONSE_QUEUE)
                .withArgument("x-message-ttl", 60000) // 60 second TTL for responses
                .build();
    }

    @Bean
    public Binding gdprExportResponseBinding(Queue gdprExportResponseQueue, TopicExchange gdprExchange) {
        return BindingBuilder.bind(gdprExportResponseQueue)
                .to(gdprExchange)
                .with("gdpr.export.response");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setExchange(exchange);
        return template;
    }
}
