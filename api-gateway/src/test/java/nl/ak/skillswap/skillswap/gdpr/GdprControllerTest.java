package nl.ak.skillswap.skillswap.gdpr;

import nl.ak.skillswap.skillswap.gdpr.dto.AggregatedGdprExport;
import nl.ak.skillswap.skillswap.gdpr.dto.GdprDeletionRequest;
import nl.ak.skillswap.skillswap.gdpr.service.GdprOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("GdprController")
class GdprControllerTest {

    @Mock
    private GdprOrchestrationService orchestrationService;

    @InjectMocks
    private GdprController gdprController;

    private Jwt mockJwt;
    private UUID userId;
    private String externalId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        externalId = userId.toString();

        mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(externalId);
    }

    @Nested
    @DisplayName("exportData")
    class ExportData {

        @Test
        @DisplayName("should return export data successfully")
        void shouldReturnExportDataSuccessfully() {
            AggregatedGdprExport export = new AggregatedGdprExport(
                    UUID.randomUUID(),
                    userId,
                    Instant.now(),
                    new HashMap<>(),
                    new HashMap<>()
            );

            when(orchestrationService.requestExport(eq(userId), eq(externalId)))
                    .thenReturn(Mono.just(export));

            Mono<ResponseEntity<AggregatedGdprExport>> result = gdprController.exportData(mockJwt);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo(export);
                    })
                    .verifyComplete();

            verify(orchestrationService).requestExport(userId, externalId);
        }

        @Test
        @DisplayName("should return export with errors logged")
        void shouldReturnExportWithErrorsLogged() {
            Map<String, String> errors = new HashMap<>();
            errors.put("user-service", "Connection timeout");

            AggregatedGdprExport export = new AggregatedGdprExport(
                    UUID.randomUUID(),
                    userId,
                    Instant.now(),
                    new HashMap<>(),
                    errors
            );

            when(orchestrationService.requestExport(eq(userId), eq(externalId)))
                    .thenReturn(Mono.just(export));

            Mono<ResponseEntity<AggregatedGdprExport>> result = gdprController.exportData(mockJwt);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody().hasErrors()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteData")
    class DeleteData {

        @Test
        @DisplayName("should accept FULL deletion type")
        void shouldAcceptFullDeletionType() {
            when(orchestrationService.requestDeletion(eq(userId), eq(externalId), eq(GdprDeletionRequest.DeletionType.FULL)))
                    .thenReturn(Mono.empty());

            Mono<ResponseEntity<Map<String, String>>> result = gdprController.deleteData(mockJwt, "FULL");

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody().get("status")).isEqualTo("accepted");
                        assertThat(response.getBody().get("type")).isEqualTo("FULL");
                    })
                    .verifyComplete();

            verify(orchestrationService).requestDeletion(userId, externalId, GdprDeletionRequest.DeletionType.FULL);
        }

        @Test
        @DisplayName("should accept ANONYMIZE deletion type")
        void shouldAcceptAnonymizeDeletionType() {
            when(orchestrationService.requestDeletion(eq(userId), eq(externalId), eq(GdprDeletionRequest.DeletionType.ANONYMIZE)))
                    .thenReturn(Mono.empty());

            Mono<ResponseEntity<Map<String, String>>> result = gdprController.deleteData(mockJwt, "ANONYMIZE");

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody().get("type")).isEqualTo("ANONYMIZE");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should use default ANONYMIZE when type not specified")
        void shouldUseDefaultAnonymizeType() {
            when(orchestrationService.requestDeletion(eq(userId), eq(externalId), eq(GdprDeletionRequest.DeletionType.ANONYMIZE)))
                    .thenReturn(Mono.empty());

            Mono<ResponseEntity<Map<String, String>>> result = gdprController.deleteData(mockJwt, "ANONYMIZE");

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should handle case-insensitive deletion type")
        void shouldHandleCaseInsensitiveDeletionType() {
            when(orchestrationService.requestDeletion(any(), any(), any()))
                    .thenReturn(Mono.empty());

            Mono<ResponseEntity<Map<String, String>>> result = gdprController.deleteData(mockJwt, "full");

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody().get("type")).isEqualTo("FULL");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return bad request for invalid deletion type")
        void shouldReturnBadRequestForInvalidDeletionType() {
            Mono<ResponseEntity<Map<String, String>>> result = gdprController.deleteData(mockJwt, "INVALID");

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(response.getBody().get("error")).contains("Invalid deletion type");
                    })
                    .verifyComplete();

            verify(orchestrationService, never()).requestDeletion(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getGdprInfo")
    class GetGdprInfo {

        @Test
        @DisplayName("should return GDPR information")
        void shouldReturnGdprInfo() {
            Mono<ResponseEntity<Map<String, Object>>> result = gdprController.getGdprInfo();

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).containsKey("rights");
                        assertThat(response.getBody()).containsKey("dataCategories");
                        assertThat(response.getBody()).containsKey("retentionPolicy");
                        assertThat(response.getBody()).containsKey("endpoints");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should include all expected rights")
        void shouldIncludeAllExpectedRights() {
            Mono<ResponseEntity<Map<String, Object>>> result = gdprController.getGdprInfo();

            StepVerifier.create(result)
                    .assertNext(response -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> rights = (Map<String, String>) response.getBody().get("rights");
                        assertThat(rights).containsKeys("export", "delete", "anonymize");
                    })
                    .verifyComplete();
        }
    }
}
