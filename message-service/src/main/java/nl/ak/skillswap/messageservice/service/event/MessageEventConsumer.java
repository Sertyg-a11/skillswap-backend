package nl.ak.skillswap.messageservice.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.config.RabbitMQConfig;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import nl.ak.skillswap.messageservice.service.RealTimeMessagingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes message events from RabbitMQ and broadcasts via WebSocket.
 * This enables horizontal scaling - any instance can receive and broadcast.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventConsumer {

    private final MessageRepository messageRepository;
    private final RealTimeMessagingService realTimeMessagingService;

    @RabbitListener(queues = RabbitMQConfig.MESSAGE_CREATED_QUEUE)
    public void handleMessageCreated(MessageCreatedEvent event) {
        log.debug("Received MessageCreatedEvent: messageId={}", event.messageId());

        try {
            // Fetch full message to send to recipient
            Message message = messageRepository.findById(event.messageId())
                    .orElse(null);

            if (message != null) {
                // Send real-time notification to recipient
                realTimeMessagingService.sendMessageToUser(message);

                // Notify about unread count update
                long unreadCount = messageRepository.countByConversationIdAndRecipientIdAndReadAtIsNull(
                        event.conversationId(),
                        event.recipientId()
                );
                realTimeMessagingService.notifyConversationUpdate(
                        event.recipientId(),
                        event.conversationId(),
                        unreadCount
                );

                log.debug("Broadcasted message {} to user {}", event.messageId(), event.recipientId());
            } else {
                log.warn("Message not found: {}", event.messageId());
            }
        } catch (Exception e) {
            log.error("Failed to broadcast message {}: {}", event.messageId(), e.getMessage());
            throw e; // Re-throw to trigger retry/DLQ
        }
    }
}
