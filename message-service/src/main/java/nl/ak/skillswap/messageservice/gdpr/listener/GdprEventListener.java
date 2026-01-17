package nl.ak.skillswap.messageservice.gdpr.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.gdpr.dto.GdprExportData;
import nl.ak.skillswap.messageservice.gdpr.event.GdprDeletionRequest;
import nl.ak.skillswap.messageservice.gdpr.event.GdprExportRequest;
import nl.ak.skillswap.messageservice.gdpr.event.GdprExportResponse;
import nl.ak.skillswap.messageservice.gdpr.service.MessageGdprService;
import nl.ak.skillswap.messageservice.service.InternalUserServiceClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens for GDPR events from RabbitMQ and processes them.
 * Handles both export requests (with reply) and deletion requests (fire-and-forget).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GdprEventListener {

    private static final String SERVICE_NAME = "message-service";

    private final MessageGdprService gdprService;
    private final RabbitTemplate rabbitTemplate;
    private final InternalUserServiceClient userServiceClient;

    /**
     * Handle GDPR export request.
     * Exports user data and sends response to reply queue.
     */
    @RabbitListener(queues = "${app.gdpr.queue.export:gdpr.export.message-service}")
    public void handleExportRequest(GdprExportRequest request) {
        log.info("Received GDPR export request: correlationId={}, userId={}, externalId={}",
                request.correlationId(), request.userId(), request.userExternalId());

        GdprExportResponse response;
        try {
            // Resolve external ID (Keycloak sub) to database UUID
            // Messages are stored with database UUIDs, not Keycloak external IDs
            UUID databaseUserId = userServiceClient.resolveExternalIdToDatabaseId(request.userExternalId())
                    .orElseThrow(() -> new RuntimeException("User not found for external ID: " + request.userExternalId()));

            log.debug("Resolved external ID {} to database ID {}", request.userExternalId(), databaseUserId);

            GdprExportData exportData = gdprService.exportUserData(databaseUserId);

            response = GdprExportResponse.success(
                    request.correlationId(),
                    SERVICE_NAME,
                    request.userId(),
                    exportData
            );

            log.info("GDPR export completed successfully for user {}", request.userId());

        } catch (Exception e) {
            log.error("GDPR export failed for user {}: {}", request.userId(), e.getMessage(), e);
            response = GdprExportResponse.error(
                    request.correlationId(),
                    SERVICE_NAME,
                    request.userId(),
                    e.getMessage()
            );
        }

        // Send response to reply queue
        rabbitTemplate.convertAndSend(
                "skillswap.events",
                "gdpr.export.response",
                response
        );
    }

    /**
     * Handle GDPR deletion request.
     * Deletes/anonymizes user data. This is fire-and-forget.
     */
    @RabbitListener(queues = "${app.gdpr.queue.deletion:gdpr.deletion.message-service}")
    public void handleDeletionRequest(GdprDeletionRequest request) {
        log.info("Received GDPR deletion request: correlationId={}, userId={}, externalId={}, type={}",
                request.correlationId(), request.userId(), request.userExternalId(), request.deletionType());

        try {
            // Resolve external ID (Keycloak sub) to database UUID
            // Messages are stored with database UUIDs, not Keycloak external IDs
            UUID databaseUserId = userServiceClient.resolveExternalIdToDatabaseId(request.userExternalId())
                    .orElseThrow(() -> new RuntimeException("User not found for external ID: " + request.userExternalId()));

            log.debug("Resolved external ID {} to database ID {}", request.userExternalId(), databaseUserId);

            MessageGdprService.DeletionResult result = gdprService.deleteUserData(databaseUserId, request.deletionType());

            log.info("GDPR deletion completed for user {}: {} anonymized, {} deleted",
                    request.userId(), result.messagesAnonymized(), result.messagesDeleted());

        } catch (Exception e) {
            log.error("GDPR deletion failed for user {}: {}", request.userId(), e.getMessage(), e);
            // In production, you might want to send this to a dead-letter queue
            // or notify administrators
        }
    }
}
