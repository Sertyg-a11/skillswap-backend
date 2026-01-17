package nl.ak.skillswap.skillswap.gdpr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.skillswap.gdpr.dto.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates GDPR operations across all microservices.
 * - Export: Publishes request, waits for responses from all services
 * - Deletion: Publishes fire-and-forget request to all services
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprOrchestrationService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.gdpr.expected-services:2}")
    private int expectedServices;

    @Value("${app.gdpr.timeout-seconds:30}")
    private int timeoutSeconds;

    // Track pending export requests
    private final ConcurrentHashMap<UUID, ExportCollector> pendingExports = new ConcurrentHashMap<>();

    /**
     * Request GDPR data export from all services.
     * Waits for responses and aggregates them.
     */
    public Mono<AggregatedGdprExport> requestExport(UUID userId, String userExternalId) {
        UUID correlationId = UUID.randomUUID();
        log.info("Starting GDPR export: correlationId={}, userId={}", correlationId, userId);

        // Create collector for responses
        ExportCollector collector = new ExportCollector(correlationId, userId, expectedServices);
        pendingExports.put(correlationId, collector);

        // Publish export request
        GdprExportRequest request = new GdprExportRequest(
                correlationId,
                userId,
                userExternalId,
                Instant.now()
        );

        // Send to each service's queue via their routing keys
        rabbitTemplate.convertAndSend("gdpr.export.user-service", request);
        rabbitTemplate.convertAndSend("gdpr.export.message-service", request);
        log.info("GDPR export request published to all services: correlationId={}", correlationId);

        // Wait for responses with timeout
        return collector.waitForCompletion(Duration.ofSeconds(timeoutSeconds))
                .doFinally(signal -> {
                    pendingExports.remove(correlationId);
                    log.info("GDPR export completed: correlationId={}, signal={}", correlationId, signal);
                });
    }

    /**
     * Request GDPR data deletion from all services.
     * This is fire-and-forget - services handle deletion independently.
     */
    public Mono<Void> requestDeletion(UUID userId, String userExternalId, GdprDeletionRequest.DeletionType type) {
        UUID correlationId = UUID.randomUUID();
        log.info("Starting GDPR deletion: correlationId={}, userId={}, type={}", correlationId, userId, type);

        GdprDeletionRequest request = new GdprDeletionRequest(
                correlationId,
                userId,
                userExternalId,
                Instant.now(),
                type
        );

        return Mono.fromRunnable(() -> {
            // Send to each service's queue via their routing keys
            rabbitTemplate.convertAndSend("gdpr.deletion.user-service", request);
            rabbitTemplate.convertAndSend("gdpr.deletion.message-service", request);
            log.info("GDPR deletion request published to all services: correlationId={}", correlationId);
        });
    }

    /**
     * Handle export responses from services.
     */
    @RabbitListener(queues = "gdpr.export.response.gateway")
    public void handleExportResponse(GdprExportResponse response) {
        log.info("Received GDPR export response: correlationId={}, service={}, success={}",
                response.correlationId(), response.serviceName(), response.success());

        ExportCollector collector = pendingExports.get(response.correlationId());
        if (collector != null) {
            collector.addResponse(response);
        } else {
            log.warn("No pending export for correlationId: {}", response.correlationId());
        }
    }

    /**
     * Collector for aggregating export responses from multiple services.
     */
    private static class ExportCollector {
        private final UUID correlationId;
        private final UUID userId;
        private final int expectedCount;
        private final Map<String, Object> serviceData = new ConcurrentHashMap<>();
        private final Map<String, String> errors = new ConcurrentHashMap<>();
        private final Sinks.One<AggregatedGdprExport> sink = Sinks.one();

        ExportCollector(UUID correlationId, UUID userId, int expectedCount) {
            this.correlationId = correlationId;
            this.userId = userId;
            this.expectedCount = expectedCount;
        }

        void addResponse(GdprExportResponse response) {
            if (response.success()) {
                serviceData.put(response.serviceName(), response.data());
            } else {
                errors.put(response.serviceName(), response.errorMessage());
            }

            // Check if all responses received
            if (serviceData.size() + errors.size() >= expectedCount) {
                complete();
            }
        }

        void complete() {
            AggregatedGdprExport result = new AggregatedGdprExport(
                    correlationId,
                    userId,
                    Instant.now(),
                    new HashMap<>(serviceData),
                    new HashMap<>(errors)
            );
            sink.tryEmitValue(result);
        }

        Mono<AggregatedGdprExport> waitForCompletion(Duration timeout) {
            return sink.asMono()
                    .timeout(timeout)
                    .onErrorResume(e -> {
                        // On timeout, return partial results
                        return Mono.just(new AggregatedGdprExport(
                                correlationId,
                                userId,
                                Instant.now(),
                                new HashMap<>(serviceData),
                                Map.of("timeout", "Not all services responded within timeout. Partial data returned.")
                        ));
                    });
        }
    }
}
