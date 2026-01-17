package nl.ak.skillswap.userservice.gdpr.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.userservice.gdpr.event.GdprDeletionRequest;
import nl.ak.skillswap.userservice.gdpr.event.GdprExportRequest;
import nl.ak.skillswap.userservice.gdpr.event.GdprExportResponse;
import nl.ak.skillswap.userservice.service.GdprService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens for GDPR events from RabbitMQ and processes them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GdprEventListener {

    private static final String SERVICE_NAME = "user-service";

    private final GdprService gdprService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Handle GDPR export request.
     */
    @RabbitListener(queues = "${app.gdpr.queue.export:gdpr.export.user-service}")
    public void handleExportRequest(GdprExportRequest request) {
        log.info("Received GDPR export request: correlationId={}, userId={}",
                request.correlationId(), request.userId());

        GdprExportResponse response;
        try {
            var exportData = gdprService.exportByExternalId(request.userExternalId());

            if (exportData.isPresent()) {
                response = GdprExportResponse.success(
                        request.correlationId(),
                        SERVICE_NAME,
                        request.userId(),
                        exportData.get()
                );
                log.info("GDPR export completed successfully for user {}", request.userId());
            } else {
                response = GdprExportResponse.error(
                        request.correlationId(),
                        SERVICE_NAME,
                        request.userId(),
                        "User not found"
                );
                log.warn("GDPR export - user not found: {}", request.userExternalId());
            }

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
     */
    @RabbitListener(queues = "${app.gdpr.queue.deletion:gdpr.deletion.user-service}")
    public void handleDeletionRequest(GdprDeletionRequest request) {
        log.info("Received GDPR deletion request: correlationId={}, userId={}, type={}",
                request.correlationId(), request.userId(), request.deletionType());

        try {
            GdprService.DeletionResult result = gdprService.deleteByExternalId(
                    request.userExternalId(),
                    request.deletionType()
            );

            log.info("GDPR deletion completed for user {}: success={}, message={}",
                    request.userId(), result.success(), result.message());

        } catch (Exception e) {
            log.error("GDPR deletion failed for user {}: {}", request.userId(), e.getMessage(), e);
        }
    }
}
