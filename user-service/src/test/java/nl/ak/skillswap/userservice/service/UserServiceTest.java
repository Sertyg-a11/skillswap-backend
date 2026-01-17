package nl.ak.skillswap.userservice.service;

import nl.ak.skillswap.userservice.domain.PrivacyEvent;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.messaging.UserDeletedEvent;
import nl.ak.skillswap.userservice.messaging.UserEventPublisher;
import nl.ak.skillswap.userservice.repository.PrivacyEventRepository;
import nl.ak.skillswap.userservice.repository.UserRepository;
import nl.ak.skillswap.userservice.support.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PrivacyEventRepository privacyEventRepository;

    @Mock
    private UserEventPublisher userEventPublisher;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .externalId("keycloak-sub-123")
                .email("test@example.com")
                .displayName("Test User")
                .active(true)
                .allowMatching(true)
                .allowEmails(true)
                .build();
    }

    @Nested
    @DisplayName("syncFromKeycloak")
    class SyncFromKeycloak {

        @Test
        @DisplayName("should return existing user when found by externalId")
        void shouldReturnExistingUserByExternalId() {
            when(userRepository.findByExternalId("keycloak-sub-123")).thenReturn(Optional.of(testUser));

            User result = userService.syncFromKeycloak("keycloak-sub-123", "newemail@example.com", "New Name");

            assertThat(result).isEqualTo(testUser);
            assertThat(result.getEmail()).isEqualTo("newemail@example.com");
            assertThat(result.getDisplayName()).isEqualTo("New Name");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should update externalId when user found by email")
        void shouldUpdateExternalIdWhenFoundByEmail() {
            String oldExternalId = "old-external-id";
            String newExternalId = "new-external-id";
            testUser.setExternalId(oldExternalId);

            when(userRepository.findByExternalId(newExternalId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            User result = userService.syncFromKeycloak(newExternalId, "test@example.com", "Updated Name");

            assertThat(result.getExternalId()).isEqualTo(newExternalId);
            assertThat(result.getDisplayName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("should create new user when not found")
        void shouldCreateNewUserWhenNotFound() {
            String externalId = "new-external-id";
            String email = "newuser@example.com";
            String displayName = "New User";

            when(userRepository.findByExternalId(externalId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            User result = userService.syncFromKeycloak(externalId, email, displayName);

            assertThat(result.getExternalId()).isEqualTo(externalId);
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getDisplayName()).isEqualTo(displayName);
            assertThat(result.isActive()).isTrue();
            assertThat(result.isAllowMatching()).isTrue();
            assertThat(result.isAllowEmails()).isTrue();
        }

        @Test
        @DisplayName("should use default values when email and displayName are null")
        void shouldUseDefaultValuesWhenNullProvided() {
            String externalId = "new-external-id";

            when(userRepository.findByExternalId(externalId)).thenReturn(Optional.empty());
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            User result = userService.syncFromKeycloak(externalId, null, null);

            assertThat(result.getEmail()).isEqualTo("unknown@example.com");
            assertThat(result.getDisplayName()).isEqualTo("User");
        }

        @Test
        @DisplayName("should handle race condition with DataIntegrityViolationException")
        void shouldHandleRaceCondition() {
            String externalId = "race-condition-external-id";
            String email = "race@example.com";

            when(userRepository.findByExternalId(externalId))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(testUser));
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            User result = userService.syncFromKeycloak(externalId, email, "Name");

            assertThat(result).isEqualTo(testUser);
        }

        @Test
        @DisplayName("should not update displayName if blank")
        void shouldNotUpdateDisplayNameIfBlank() {
            testUser.setDisplayName("Original Name");
            when(userRepository.findByExternalId("keycloak-sub-123")).thenReturn(Optional.of(testUser));

            User result = userService.syncFromKeycloak("keycloak-sub-123", "test@example.com", "   ");

            assertThat(result.getDisplayName()).isEqualTo("Original Name");
        }
    }

    @Nested
    @DisplayName("getActiveOrThrow")
    class GetActiveOrThrow {

        @Test
        @DisplayName("should return user when found and not deleted")
        void shouldReturnUserWhenFound() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

            User result = userService.getActiveOrThrow(userId);

            assertThat(result).isEqualTo(testUser);
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getActiveOrThrow(userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("should update profile and create privacy event")
        void shouldUpdateProfileAndCreatePrivacyEvent() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

            User result = userService.updateProfile(userId, "New Name", "Europe/Amsterdam", "New bio");

            assertThat(result.getDisplayName()).isEqualTo("New Name");
            assertThat(result.getTimeZone()).isEqualTo("Europe/Amsterdam");
            assertThat(result.getBio()).isEqualTo("New bio");

            ArgumentCaptor<PrivacyEvent> eventCaptor = ArgumentCaptor.forClass(PrivacyEvent.class);
            verify(privacyEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile(userId, "Name", null, null))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updatePreferences")
    class UpdatePreferences {

        @Test
        @DisplayName("should update preferences and create privacy event")
        void shouldUpdatePreferencesAndCreatePrivacyEvent() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

            User result = userService.updatePreferences(userId, false, false);

            assertThat(result.isAllowMatching()).isFalse();
            assertThat(result.isAllowEmails()).isFalse();

            verify(privacyEventRepository).save(any(PrivacyEvent.class));
        }
    }

    @Nested
    @DisplayName("softDeleteAccount")
    class SoftDeleteAccount {

        @Test
        @DisplayName("should soft delete user and publish event")
        void shouldSoftDeleteAndPublishEvent() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

            userService.softDeleteAccount(userId);

            assertThat(testUser.getDeletedAt()).isNotNull();
            assertThat(testUser.isActive()).isFalse();

            verify(privacyEventRepository).save(any(PrivacyEvent.class));

            ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
            verify(userEventPublisher).publishUserDeleted(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.softDeleteAccount(userId))
                    .isInstanceOf(NotFoundException.class);

            verify(userEventPublisher, never()).publishUserDeleted(any());
        }
    }
}
