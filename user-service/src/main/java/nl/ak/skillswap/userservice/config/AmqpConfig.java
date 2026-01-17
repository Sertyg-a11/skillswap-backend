package nl.ak.skillswap.userservice.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    @Value("${app.messaging.exchange:skillswap.events}")
    private String exchangeName;

    @Value("${app.gdpr.queue.export:gdpr.export.user-service}")
    private String gdprExportQueue;

    @Value("${app.gdpr.queue.deletion:gdpr.deletion.user-service}")
    private String gdprDeletionQueue;

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    TopicExchange skillswapExchange() {
        return new TopicExchange(exchangeName);
    }

    // GDPR Export Queue
    @Bean
    Queue gdprExportQueue() {
        return new Queue(gdprExportQueue, true);
    }

    @Bean
    Binding gdprExportBinding(Queue gdprExportQueue, TopicExchange skillswapExchange) {
        return BindingBuilder.bind(gdprExportQueue)
                .to(skillswapExchange)
                .with("gdpr.export.user-service");
    }

    // GDPR Deletion Queue
    @Bean
    Queue gdprDeletionQueue() {
        return new Queue(gdprDeletionQueue, true);
    }

    @Bean
    Binding gdprDeletionBinding(Queue gdprDeletionQueue, TopicExchange skillswapExchange) {
        return BindingBuilder.bind(gdprDeletionQueue)
                .to(skillswapExchange)
                .with("gdpr.deletion.user-service");
    }
}
