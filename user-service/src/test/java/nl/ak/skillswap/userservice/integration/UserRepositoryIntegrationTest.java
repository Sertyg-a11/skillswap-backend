package nl.ak.skillswap.userservice.integration;

import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRepository Integration Tests")
class UserRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User activeUser;
    private User inactiveUser;
    private User deletedUser;
    private User nonMatchableUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        activeUser = userRepository.save(User.builder()
                .externalId("ext-active-" + UUID.randomUUID())
                .email("active@test.com")
                .displayName("Active User")
                .active(true)
                .allowMatching(true)
                .allowEmails(true)
                .build());

        inactiveUser = userRepository.save(User.builder()
                .externalId("ext-inactive-" + UUID.randomUUID())
                .email("inactive@test.com")
                .displayName("Inactive User")
                .active(false)
                .allowMatching(true)
                .allowEmails(true)
                .build());

        deletedUser = User.builder()
                .externalId("ext-deleted-" + UUID.randomUUID())
                .email("deleted@test.com")
                .displayName("Deleted User")
                .active(true)
                .allowMatching(true)
                .allowEmails(true)
                .build();
        deletedUser.softDeleteNow();
        userRepository.save(deletedUser);

        nonMatchableUser = userRepository.save(User.builder()
                .externalId("ext-nomatch-" + UUID.randomUUID())
                .email("nomatch@test.com")
                .displayName("Non Matchable User")
                .active(true)
                .allowMatching(false)
                .allowEmails(true)
                .build());
    }

    @Nested
    @DisplayName("findByExternalId")
    class FindByExternalId {

        @Test
        @DisplayName("should find user by external ID")
        void shouldFindUserByExternalId() {
            Optional<User> result = userRepository.findByExternalId(activeUser.getExternalId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(activeUser.getId());
        }

        @Test
        @DisplayName("should return empty when external ID not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> result = userRepository.findByExternalId("non-existent");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("should find user by email")
        void shouldFindUserByEmail() {
            Optional<User> result = userRepository.findByEmail("active@test.com");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(activeUser.getId());
        }

        @Test
        @DisplayName("should return empty when email not found")
        void shouldReturnEmptyWhenEmailNotFound() {
            Optional<User> result = userRepository.findByEmail("nonexistent@test.com");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndDeletedAtIsNull")
    class FindByIdAndDeletedAtIsNull {

        @Test
        @DisplayName("should find active user")
        void shouldFindActiveUser() {
            Optional<User> result = userRepository.findByIdAndDeletedAtIsNull(activeUser.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(activeUser.getId());
        }

        @Test
        @DisplayName("should not find deleted user")
        void shouldNotFindDeletedUser() {
            Optional<User> result = userRepository.findByIdAndDeletedAtIsNull(deletedUser.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchByDisplayName")
    class SearchByDisplayName {

        @Test
        @DisplayName("should find users by partial display name match")
        void shouldFindUsersByPartialMatch() {
            UUID excludeId = UUID.randomUUID();
            List<User> results = userRepository.searchByDisplayName(
                    "Active", excludeId, PageRequest.of(0, 10));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDisplayName()).isEqualTo("Active User");
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            UUID excludeId = UUID.randomUUID();
            List<User> results = userRepository.searchByDisplayName(
                    "active", excludeId, PageRequest.of(0, 10));

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("should exclude specified user")
        void shouldExcludeSpecifiedUser() {
            List<User> results = userRepository.searchByDisplayName(
                    "User", activeUser.getId(), PageRequest.of(0, 10));

            assertThat(results).noneMatch(u -> u.getId().equals(activeUser.getId()));
        }

        @Test
        @DisplayName("should not return inactive users")
        void shouldNotReturnInactiveUsers() {
            UUID excludeId = UUID.randomUUID();
            List<User> results = userRepository.searchByDisplayName(
                    "Inactive", excludeId, PageRequest.of(0, 10));

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should not return deleted users")
        void shouldNotReturnDeletedUsers() {
            UUID excludeId = UUID.randomUUID();
            List<User> results = userRepository.searchByDisplayName(
                    "Deleted", excludeId, PageRequest.of(0, 10));

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should not return non-matchable users")
        void shouldNotReturnNonMatchableUsers() {
            UUID excludeId = UUID.randomUUID();
            List<User> results = userRepository.searchByDisplayName(
                    "Non Matchable", excludeId, PageRequest.of(0, 10));

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should respect pagination")
        void shouldRespectPagination() {
            // Add more users for pagination test
            for (int i = 0; i < 5; i++) {
                userRepository.save(User.builder()
                        .externalId("ext-page-" + i + "-" + UUID.randomUUID())
                        .email("page" + i + "@test.com")
                        .displayName("Page Test User " + i)
                        .active(true)
                        .allowMatching(true)
                        .allowEmails(true)
                        .build());
            }

            UUID excludeId = UUID.randomUUID();
            List<User> page1 = userRepository.searchByDisplayName(
                    "Page Test", excludeId, PageRequest.of(0, 2));
            List<User> page2 = userRepository.searchByDisplayName(
                    "Page Test", excludeId, PageRequest.of(1, 2));

            assertThat(page1).hasSize(2);
            assertThat(page2).hasSize(2);
            assertThat(page1.get(0).getId()).isNotEqualTo(page2.get(0).getId());
        }
    }

    @Nested
    @DisplayName("findActiveMatchableByIds")
    class FindActiveMatchableByIds {

        @Test
        @DisplayName("should find active matchable users by IDs")
        void shouldFindActiveMatchableUsers() {
            List<User> results = userRepository.findActiveMatchableByIds(
                    List.of(activeUser.getId(), inactiveUser.getId(), deletedUser.getId(), nonMatchableUser.getId())
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(activeUser.getId());
        }

        @Test
        @DisplayName("should return empty list when no matching users")
        void shouldReturnEmptyWhenNoMatch() {
            List<User> results = userRepository.findActiveMatchableByIds(
                    List.of(UUID.randomUUID(), UUID.randomUUID())
            );

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByIdAndDeletedAtIsNullAndActiveTrue")
    class ExistsByIdAndDeletedAtIsNullAndActiveTrue {

        @Test
        @DisplayName("should return true for active user")
        void shouldReturnTrueForActiveUser() {
            boolean exists = userRepository.existsByIdAndDeletedAtIsNullAndActiveTrue(activeUser.getId());
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false for inactive user")
        void shouldReturnFalseForInactiveUser() {
            boolean exists = userRepository.existsByIdAndDeletedAtIsNullAndActiveTrue(inactiveUser.getId());
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false for deleted user")
        void shouldReturnFalseForDeletedUser() {
            boolean exists = userRepository.existsByIdAndDeletedAtIsNullAndActiveTrue(deletedUser.getId());
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false for non-existent user")
        void shouldReturnFalseForNonExistentUser() {
            boolean exists = userRepository.existsByIdAndDeletedAtIsNullAndActiveTrue(UUID.randomUUID());
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByExternalIdAndDeletedAtIsNullAndActiveTrue")
    class ExistsByExternalIdAndDeletedAtIsNullAndActiveTrue {

        @Test
        @DisplayName("should return true for active user")
        void shouldReturnTrueForActiveUser() {
            boolean exists = userRepository.existsByExternalIdAndDeletedAtIsNullAndActiveTrue(activeUser.getExternalId());
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false for inactive user")
        void shouldReturnFalseForInactiveUser() {
            boolean exists = userRepository.existsByExternalIdAndDeletedAtIsNullAndActiveTrue(inactiveUser.getExternalId());
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false for deleted user")
        void shouldReturnFalseForDeletedUser() {
            boolean exists = userRepository.existsByExternalIdAndDeletedAtIsNullAndActiveTrue(deletedUser.getExternalId());
            assertThat(exists).isFalse();
        }
    }
}
