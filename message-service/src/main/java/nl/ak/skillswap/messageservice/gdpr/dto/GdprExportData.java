package nl.ak.skillswap.messageservice.gdpr.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GDPR export data from message-service.
 * Contains all messages and conversations for a user.
 */
public record GdprExportData(
        String serviceName,
        Instant exportedAt,
        List<ConversationExport> conversations,
        List<MessageExport> messagesSent,
        List<MessageExport> messagesReceived,
        ExportSummary summary
) {
    public record ConversationExport(
            UUID id,
            UUID otherUserId,
            Instant createdAt,
            Instant lastMessageAt
    ) {}

    public record MessageExport(
            UUID id,
            UUID conversationId,
            UUID otherPartyId,
            String body,
            Instant createdAt,
            Instant readAt,
            boolean isSent
    ) {}

    public record ExportSummary(
            int totalConversations,
            int totalMessagesSent,
            int totalMessagesReceived
    ) {}

    public static GdprExportData empty() {
        return new GdprExportData(
                "message-service",
                Instant.now(),
                List.of(),
                List.of(),
                List.of(),
                new ExportSummary(0, 0, 0)
        );
    }
}
