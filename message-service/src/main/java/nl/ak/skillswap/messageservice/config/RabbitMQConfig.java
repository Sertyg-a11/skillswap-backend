package nl.ak.skillswap.messageservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for event-driven messaging.
 * Supports message broadcasting across multiple service instances.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routingKeyMessageCreated}")
    private String messageCreatedRoutingKey;

    public static final String MESSAGE_CREATED_QUEUE = "message.created.websocket";

    // GDPR Queue names
    public static final String GDPR_EXPORT_QUEUE = "gdpr.export.message-service";
    public static final String GDPR_DELETION_QUEUE = "gdpr.deletion.message-service";

    @Bean
    public TopicExchange skillswapExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue messageCreatedQueue() {
        return QueueBuilder.durable(MESSAGE_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", exchange + ".dlx")
                .withArgument("x-dead-letter-routing-key", "dlq.message.created")
                .build();
    }

    @Bean
    public Binding messageCreatedBinding(Queue messageCreatedQueue, TopicExchange skillswapExchange) {
        return BindingBuilder.bind(messageCreatedQueue)
                .to(skillswapExchange)
                .with(messageCreatedRoutingKey);
    }

    // ==================== GDPR Queues ====================

    @Bean
    public Queue gdprExportQueue() {
        return QueueBuilder.durable(GDPR_EXPORT_QUEUE).build();
    }

    @Bean
    public Queue gdprDeletionQueue() {
        return QueueBuilder.durable(GDPR_DELETION_QUEUE).build();
    }

    @Bean
    public Binding gdprExportBinding(Queue gdprExportQueue, TopicExchange skillswapExchange) {
        return BindingBuilder.bind(gdprExportQueue)
                .to(skillswapExchange)
                .with("gdpr.export.message-service");
    }

    @Bean
    public Binding gdprDeletionBinding(Queue gdprDeletionQueue, TopicExchange skillswapExchange) {
        return BindingBuilder.bind(gdprDeletionQueue)
                .to(skillswapExchange)
                .with("gdpr.deletion.message-service");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
