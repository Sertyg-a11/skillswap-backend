package nl.ak.skillswap.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.userservice.domain.PrivacyEvent;
import nl.ak.skillswap.userservice.domain.PrivacyEventType;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.support.NotFoundException;
import nl.ak.skillswap.userservice.messaging.UserDeletedEvent;
import nl.ak.skillswap.userservice.messaging.UserEventPublisher;
import nl.ak.skillswap.userservice.repository.PrivacyEventRepository;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PrivacyEventRepository privacyEvents;
    private final UserEventPublisher userEventPublisher;

    @Transactional
    public User syncFromKeycloak(String externalId, String email, String displayName) {
        // First, try to find by externalId (Keycloak sub claim)
        return users.findByExternalId(externalId)
                .map(existing -> {
                    // Keep values in sync for MVP
                    if (email != null) existing.setEmail(email);
                    if (displayName != null && !displayName.isBlank()) existing.setDisplayName(displayName);
                    return existing;
                })
                .orElseGet(() -> {
                    // Check if user exists by email (handles Keycloak realm reset scenario)
                    if (email != null) {
                        var existingByEmail = users.findByEmail(email);
                        if (existingByEmail.isPresent()) {
                            User existing = existingByEmail.get();
                            log.info("Updating externalId for existing user email={} oldExternalId={} newExternalId={}",
                                    email, existing.getExternalId(), externalId);
                            existing.setExternalId(externalId);
                            if (displayName != null && !displayName.isBlank()) existing.setDisplayName(displayName);
                            return existing;
                        }
                    }

                    // Create new user
                    try {
                        User newUser = User.builder()
                                .externalId(externalId)
                                .email(email != null ? email : "unknown@example.com")
                                .displayName(displayName != null ? displayName : "User")
                                .active(true)
                                .allowMatching(true)
                                .allowEmails(true)
                                .build();
                        User saved = users.saveAndFlush(newUser);
                        log.info("Created local user id={} externalId={}", saved.getId(), externalId);
                        return saved;
                    } catch (DataIntegrityViolationException e) {
                        // Race condition: another request created the user, fetch it
                        log.debug("Constraint violation, fetching existing user externalId={} email={}", externalId, email);
                        return users.findByExternalId(externalId)
                                .or(() -> email != null ? users.findByEmail(email) : java.util.Optional.empty())
                                .orElseThrow(() -> new IllegalStateException("User should exist after constraint violation"));
                    }
                });
    }

    @Transactional(readOnly = true)
    public User getActiveOrThrow(java.util.UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("User not found or deleted"));
    }

    @Transactional
    public User updateProfile(java.util.UUID userId, String displayName, String timeZone, String bio) {
        User u = getActiveOrThrow(userId);
        u.updateProfile(displayName, timeZone, bio);
        privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.PROFILE_UPDATED, null));
        return u;
    }

    @Transactional
    public User updatePreferences(java.util.UUID userId, boolean allowMatching, boolean allowEmails) {
        User u = getActiveOrThrow(userId);
        u.updatePreferences(allowMatching, allowEmails);
        privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.PREFERENCES_CHANGED, null));
        return u;
    }

    @Transactional
    public void softDeleteAccount(java.util.UUID userId) {
        User u = getActiveOrThrow(userId);
        u.softDeleteNow();

        privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.ACCOUNT_DELETED, null));

        userEventPublisher.publishUserDeleted(new UserDeletedEvent(
                u.getId(),
                u.getExternalId(),
                u.getDeletedAt()
        ));

        log.warn("User soft-deleted and event published userId={}", userId);
    }
}
