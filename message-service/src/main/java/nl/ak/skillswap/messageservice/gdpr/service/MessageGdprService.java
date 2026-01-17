package nl.ak.skillswap.messageservice.gdpr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.gdpr.dto.GdprExportData;
import nl.ak.skillswap.messageservice.gdpr.dto.GdprExportData.*;
import nl.ak.skillswap.messageservice.gdpr.event.GdprDeletionRequest;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service handling GDPR operations for message-service.
 * Provides data export and deletion/anonymization capabilities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageGdprService {

    private static final String SERVICE_NAME = "message-service";

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Export all user data for GDPR compliance.
     * Includes all conversations and messages (sent and received).
     *
     * @param userId the user's ID (Keycloak external ID)
     * @return complete export data
     */
    @Transactional(readOnly = true)
    public GdprExportData exportUserData(UUID userId) {
        log.info("GDPR export requested for user: {}", userId);

        // Get all conversations
        List<Conversation> conversations = conversationRepository.findAllByUserId(userId);
        List<ConversationExport> conversationExports = conversations.stream()
                .map(c -> new ConversationExport(
                        c.getId(),
                        c.otherParticipant(userId),
                        c.getCreatedAt(),
                        c.getLastMessageAt()
                ))
                .toList();

        // Get all messages sent by user
        List<Message> sentMessages = messageRepository.findBySenderId(userId);
        List<MessageExport> sentExports = sentMessages.stream()
                .map(m -> toMessageExport(m, true))
                .toList();

        // Get all messages received by user
        List<Message> receivedMessages = messageRepository.findByRecipientId(userId);
        List<MessageExport> receivedExports = receivedMessages.stream()
                .map(m -> toMessageExport(m, false))
                .toList();

        ExportSummary summary = new ExportSummary(
                conversations.size(),
                sentMessages.size(),
                receivedMessages.size()
        );

        log.info("GDPR export completed for user {}: {} conversations, {} sent, {} received",
                userId, summary.totalConversations(), summary.totalMessagesSent(), summary.totalMessagesReceived());

        return new GdprExportData(
                SERVICE_NAME,
                Instant.now(),
                conversationExports,
                sentExports,
                receivedExports,
                summary
        );
    }

    /**
     * Delete/anonymize user data for GDPR compliance.
     * Strategy:
     * - Anonymize messages SENT by user (preserves conversation for recipient)
     * - Delete messages RECEIVED by user (removes their inbox)
     * - Clean up empty conversations
     *
     * @param userId the user's ID (Keycloak external ID)
     * @param deletionType type of deletion (FULL or ANONYMIZE)
     * @return deletion result summary
     */
    @Transactional
    public DeletionResult deleteUserData(UUID userId, GdprDeletionRequest.DeletionType deletionType) {
        log.info("GDPR deletion requested for user: {}, type: {}", userId, deletionType);

        int messagesAnonymized = 0;
        int messagesDeleted = 0;
        int conversationsDeleted = 0;

        if (deletionType == GdprDeletionRequest.DeletionType.FULL) {
            // Full deletion: delete all messages involving this user
            messagesDeleted += messageRepository.deleteMessagesByRecipient(userId);

            // For sent messages, we anonymize to preserve conversation history for other party
            messagesAnonymized = messageRepository.anonymizeMessagesBySender(userId);
        } else {
            // Anonymize only: just anonymize sent messages, keep received
            messagesAnonymized = messageRepository.anonymizeMessagesBySender(userId);
            messagesDeleted = messageRepository.deleteMessagesByRecipient(userId);
        }

        // Clean up empty conversations (no messages left)
        conversationsDeleted = conversationRepository.deleteEmptyConversationsByUserId(userId);

        log.info("GDPR deletion completed for user {}: {} anonymized, {} deleted, {} conversations removed",
                userId, messagesAnonymized, messagesDeleted, conversationsDeleted);

        return new DeletionResult(
                SERVICE_NAME,
                userId,
                Instant.now(),
                messagesAnonymized,
                messagesDeleted,
                conversationsDeleted,
                true,
                null
        );
    }

    private MessageExport toMessageExport(Message m, boolean isSent) {
        return new MessageExport(
                m.getId(),
                m.getConversationId(),
                isSent ? m.getRecipientId() : m.getSenderId(),
                m.getBody(),
                m.getCreatedAt(),
                m.getReadAt(),
                isSent
        );
    }

    /**
     * Result of a GDPR deletion operation.
     */
    public record DeletionResult(
            String serviceName,
            UUID userId,
            Instant deletedAt,
            int messagesAnonymized,
            int messagesDeleted,
            int conversationsDeleted,
            boolean success,
            String errorMessage
    ) {
        public static DeletionResult error(UUID userId, String errorMessage) {
            return new DeletionResult(
                    SERVICE_NAME,
                    userId,
                    Instant.now(),
                    0, 0, 0,
                    false,
                    errorMessage
            );
        }
    }
}
