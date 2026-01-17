package nl.ak.skillswap.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.userservice.domain.PrivacyEvent;
import nl.ak.skillswap.userservice.domain.PrivacyEventType;
import nl.ak.skillswap.userservice.domain.Skill;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.gdpr.event.GdprDeletionRequest;
import nl.ak.skillswap.userservice.repository.PrivacyEventRepository;
import nl.ak.skillswap.userservice.repository.SkillRepository;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GdprService {

    private static final String SERVICE_NAME = "user-service";

    private final UserService userService;
    private final UserRepository userRepository;
    private final SkillRepository skills;
    private final PrivacyEventRepository privacyEvents;

    /**
     * Export all user data by database ID.
     */
    @Transactional
    public ExportBundle exportAll(UUID userId) {
        User u = userService.getActiveOrThrow(userId);
        return exportUserData(u);
    }

    /**
     * Export all user data by external ID (Keycloak sub).
     */
    @Transactional
    public Optional<GdprExportData> exportByExternalId(String externalId) {
        return userRepository.findByExternalId(externalId)
                .map(this::exportUserData)
                .map(this::toExportData);
    }

    /**
     * Delete user data by external ID.
     */
    @Transactional
    public DeletionResult deleteByExternalId(String externalId, GdprDeletionRequest.DeletionType type) {
        log.info("GDPR deletion for externalId: {}, type: {}", externalId, type);

        Optional<User> userOpt = userRepository.findByExternalId(externalId);
        if (userOpt.isEmpty()) {
            log.warn("User not found for deletion: {}", externalId);
            return DeletionResult.notFound(externalId);
        }

        User user = userOpt.get();
        UUID userId = user.getId();

        try {
            if (type == GdprDeletionRequest.DeletionType.FULL) {
                // Full deletion - soft delete user, delete skills
                int skillsDeleted = skills.deleteByUserId(userId);
                user.softDeleteNow();
                userRepository.save(user);

                // Record deletion event
                privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.ACCOUNT_DELETED, null));

                log.info("GDPR full deletion completed: userId={}, skillsDeleted={}", userId, skillsDeleted);
                return new DeletionResult(SERVICE_NAME, externalId, Instant.now(), true, skillsDeleted, "User and skills deleted");
            } else {
                // Anonymize - clear personal data but keep record
                user.anonymize();
                userRepository.save(user);

                // Record anonymization event
                privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.ACCOUNT_DELETED, "anonymized"));

                log.info("GDPR anonymization completed: userId={}", userId);
                return new DeletionResult(SERVICE_NAME, externalId, Instant.now(), true, 0, "User data anonymized");
            }
        } catch (Exception e) {
            log.error("GDPR deletion failed for {}: {}", externalId, e.getMessage(), e);
            return DeletionResult.error(externalId, e.getMessage());
        }
    }

    private ExportBundle exportUserData(User user) {
        UUID userId = user.getId();
        List<Skill> userSkills = skills.findByUserId(userId);
        List<PrivacyEvent> events = privacyEvents.findByUserIdOrderByCreatedAtDesc(userId);

        // Audit export
        privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.DATA_EXPORTED, null));

        return new ExportBundle(user, userSkills, events);
    }

    private GdprExportData toExportData(ExportBundle bundle) {
        User u = bundle.user();
        return new GdprExportData(
                SERVICE_NAME,
                Instant.now(),
                new GdprExportData.UserData(
                        u.getId(),
                        u.getExternalId(),
                        u.getEmail(),
                        u.getDisplayName(),
                        u.getTimeZone(),
                        u.getBio(),
                        u.isActive(),
                        u.isAllowMatching(),
                        u.isAllowEmails(),
                        u.getCreatedAt()
                ),
                bundle.skills().stream()
                        .map(s -> new GdprExportData.SkillData(s.getName(), s.getLevel(), s.getCategory(), s.getDescription()))
                        .toList(),
                bundle.privacyEvents().stream()
                        .map(e -> new GdprExportData.PrivacyEventData(e.getType().name(), e.getDetails(), e.getCreatedAt()))
                        .toList(),
                new GdprExportData.Summary(bundle.skills().size(), bundle.privacyEvents().size())
        );
    }

    // ==================== DTOs ====================

    public record ExportBundle(User user, List<Skill> skills, List<PrivacyEvent> privacyEvents) {}

    public record GdprExportData(
            String serviceName,
            Instant exportedAt,
            UserData user,
            List<SkillData> skills,
            List<PrivacyEventData> privacyEvents,
            Summary summary
    ) {
        public record UserData(
                UUID id,
                String externalId,
                String email,
                String displayName,
                String timeZone,
                String bio,
                boolean active,
                boolean allowMatching,
                boolean allowEmails,
                java.time.OffsetDateTime createdAt
        ) {}

        public record SkillData(String name, String level, String category, String description) {}

        public record PrivacyEventData(String eventType, String details, java.time.OffsetDateTime createdAt) {}

        public record Summary(int totalSkills, int totalPrivacyEvents) {}
    }

    public record DeletionResult(
            String serviceName,
            String externalId,
            Instant deletedAt,
            boolean success,
            int itemsDeleted,
            String message
    ) {
        public static DeletionResult notFound(String externalId) {
            return new DeletionResult(SERVICE_NAME, externalId, Instant.now(), false, 0, "User not found");
        }

        public static DeletionResult error(String externalId, String errorMessage) {
            return new DeletionResult(SERVICE_NAME, externalId, Instant.now(), false, 0, errorMessage);
        }
    }
}
