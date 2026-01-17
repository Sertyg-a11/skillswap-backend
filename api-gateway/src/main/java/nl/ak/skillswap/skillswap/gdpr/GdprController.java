package nl.ak.skillswap.skillswap.gdpr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.skillswap.gdpr.dto.AggregatedGdprExport;
import nl.ak.skillswap.skillswap.gdpr.dto.GdprDeletionRequest;
import nl.ak.skillswap.skillswap.gdpr.service.GdprOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * GDPR Controller for data export and deletion.
 * Orchestrates requests across all microservices.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gdpr")
public class GdprController {

    private final GdprOrchestrationService orchestrationService;

    /**
     * Export all user data from all services.
     * Returns aggregated data from user-service, message-service, etc.
     */
    @GetMapping("/export")
    public Mono<ResponseEntity<AggregatedGdprExport>> exportData(@AuthenticationPrincipal Jwt jwt) {
        String externalId = jwt.getSubject();
        UUID userId = UUID.fromString(externalId);

        log.info("GDPR export requested by user: {}", externalId);

        return orchestrationService.requestExport(userId, externalId)
                .map(export -> {
                    if (export.hasErrors()) {
                        log.warn("GDPR export completed with errors: {}", export.errors());
                    }
                    return ResponseEntity.ok(export);
                });
    }

    /**
     * Delete all user data from all services.
     * This is an irreversible operation.
     *
     * @param type Deletion type: FULL (delete everything) or ANONYMIZE (anonymize where possible)
     */
    @DeleteMapping("/delete")
    public Mono<ResponseEntity<Map<String, String>>> deleteData(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ANONYMIZE") String type
    ) {
        String externalId = jwt.getSubject();
        UUID userId = UUID.fromString(externalId);

        GdprDeletionRequest.DeletionType deletionType;
        try {
            deletionType = GdprDeletionRequest.DeletionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid deletion type. Use FULL or ANONYMIZE")));
        }

        log.info("GDPR deletion requested by user: {}, type: {}", externalId, deletionType);

        return orchestrationService.requestDeletion(userId, externalId, deletionType)
                .thenReturn(ResponseEntity.ok(Map.of(
                        "status", "accepted",
                        "message", "Deletion request has been submitted. Your data will be deleted/anonymized across all services.",
                        "type", deletionType.name()
                )));
    }

    /**
     * Get information about GDPR rights and data handling.
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> getGdprInfo() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "rights", Map.of(
                        "export", "You can export all your personal data at any time",
                        "delete", "You can request deletion of your data",
                        "anonymize", "You can request anonymization of your data"
                ),
                "dataCategories", Map.of(
                        "user-service", "Profile information, skills, preferences, privacy events",
                        "message-service", "Conversations and messages"
                ),
                "retentionPolicy", "Data is retained until you request deletion",
                "endpoints", Map.of(
                        "export", "GET /api/gdpr/export",
                        "delete", "DELETE /api/gdpr/delete?type=FULL|ANONYMIZE"
                )
        )));
    }
}
