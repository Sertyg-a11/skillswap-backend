package nl.ak.skillswap.messageservice.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes message events to RabbitMQ.
 * Events are published AFTER the current transaction commits to ensure
 * the message is visible in the database when consumers process the event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routingKeyMessageCreated}")
    private String routingKey;

    /**
     * Publish a message created event after the current transaction commits.
     * This ensures the message is visible in the database when the consumer processes it.
     */
    public void publishMessageCreated(MessageCreatedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Delay publishing until after the transaction commits
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(event);
                }
            });
            log.debug("Scheduled MessageCreatedEvent for post-commit publishing: {}", event.messageId());
        } else {
            // No active transaction, publish immediately
            doPublish(event);
        }
    }

    private void doPublish(MessageCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("Published MessageCreatedEvent: {}", event.messageId());
        } catch (Exception e) {
            log.error("Failed to publish MessageCreatedEvent {}: {}", event.messageId(), e.getMessage());
        }
    }
}
