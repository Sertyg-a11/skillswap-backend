package nl.ak.skillswap.messageservice.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.service.RateLimitingService;
import nl.ak.skillswap.messageservice.service.RealTimeMessagingService;
import nl.ak.skillswap.messageservice.service.ConversationService;
import nl.ak.skillswap.messageservice.support.ForbiddenException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * WebSocket controller for real-time messaging interactions.
 * Handles typing indicators and other live updates.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final RealTimeMessagingService realTimeMessagingService;
    private final RateLimitingService rateLimitingService;
    private final ConversationService conversationService;

    /**
     * Handle typing indicator messages.
     * Client sends to: /app/typing
     */
    @MessageMapping("/typing")
    public void handleTyping(@Payload TypingMessage message, Principal principal) {
        UUID userId = extractUserId(principal);

        // Rate limit typing indicators to prevent spam
        rateLimitingService.checkTypingRateLimit(userId);

        // Validate user is part of the conversation
        var conversation = conversationService.getOrThrow(message.conversationId());
        if (!conversation.involves(userId)) {
            log.warn("User {} attempted typing indicator for conversation they're not in: {}",
                    userId, message.conversationId());
            throw new ForbiddenException("Not part of this conversation");
        }

        // Send typing indicator to the other participant
        UUID otherUserId = conversation.otherParticipant(userId);
        realTimeMessagingService.sendTypingIndicator(userId, otherUserId, message.conversationId(), message.isTyping());
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof UUID userId) {
                return userId;
            }
        }
        throw new IllegalStateException("Invalid authentication");
    }

    public record TypingMessage(UUID conversationId, boolean isTyping) {}
}
