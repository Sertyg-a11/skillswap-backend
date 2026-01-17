package nl.ak.skillswap.messageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.api.dto.MessageDto;
import nl.ak.skillswap.messageservice.domain.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for real-time message delivery via WebSocket.
 * Handles broadcasting messages to connected users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeMessagingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionService sessionService;

    /**
     * Send a new message to the recipient in real-time.
     * The message is sent to the user's personal queue.
     */
    public void sendMessageToUser(Message message) {
        UUID recipientId = message.getRecipientId();

        if (sessionService.isUserOnline(recipientId)) {
            MessageDto dto = MessageDto.from(message);

            // Send to user's personal queue
            // Client subscribes to: /user/queue/messages
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/messages",
                    dto
            );

            log.debug("Sent real-time message {} to user {}", message.getId(), recipientId);
        } else {
            log.debug("User {} is offline, message {} will be retrieved on next poll",
                    recipientId, message.getId());
        }
    }

    /**
     * Notify a user about conversation updates (e.g., new message indicator).
     */
    public void notifyConversationUpdate(UUID userId, UUID conversationId, long unreadCount) {
        if (sessionService.isUserOnline(userId)) {
            var notification = new ConversationUpdateNotification(conversationId, unreadCount);

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/conversations",
                    notification
            );

            log.debug("Sent conversation update to user {}: conversation={}, unread={}",
                    userId, conversationId, unreadCount);
        }
    }

    /**
     * Notify a user that their messages have been read.
     */
    public void notifyMessagesRead(UUID senderId, UUID conversationId) {
        if (sessionService.isUserOnline(senderId)) {
            var notification = new MessagesReadNotification(conversationId);

            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    "/queue/read-receipts",
                    notification
            );

            log.debug("Sent read receipt to user {} for conversation {}", senderId, conversationId);
        }
    }

    /**
     * Broadcast typing indicator to the other participant.
     */
    public void sendTypingIndicator(UUID fromUserId, UUID toUserId, UUID conversationId, boolean isTyping) {
        if (sessionService.isUserOnline(toUserId)) {
            var indicator = new TypingIndicator(conversationId, fromUserId, isTyping);

            messagingTemplate.convertAndSendToUser(
                    toUserId.toString(),
                    "/queue/typing",
                    indicator
            );
        }
    }

    // DTOs for WebSocket notifications
    public record ConversationUpdateNotification(UUID conversationId, long unreadCount) {}
    public record MessagesReadNotification(UUID conversationId) {}
    public record TypingIndicator(UUID conversationId, UUID userId, boolean isTyping) {}
}
