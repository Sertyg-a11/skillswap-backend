package nl.ak.skillswap.userservice.integration;

import nl.ak.skillswap.userservice.api.dto.UpdatePreferencesRequest;
import nl.ak.skillswap.userservice.api.dto.UpdateProfileRequest;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("UserController Integration Tests")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class UserControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User existingUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        existingUser = userRepository.save(User.builder()
                .externalId("existing-user-ext-id")
                .email("existing@test.com")
                .displayName("Existing User")
                .active(true)
                .allowMatching(true)
                .allowEmails(true)
                .build());
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMe {

        @Test
        @DisplayName("should return current user and sync from Keycloak")
        void shouldReturnCurrentUserAndSync() throws Exception {
            mockMvc.perform(get("/api/users/me")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL)
                                            .claim("preferred_username", TestSecurityConfig.TEST_USERNAME))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is(TestSecurityConfig.TEST_EMAIL)))
                    .andExpect(jsonPath("$.displayName", is(TestSecurityConfig.TEST_USERNAME)))
                    .andExpect(jsonPath("$.externalId", is(TestSecurityConfig.TEST_EXTERNAL_ID)));

            // Verify user was created in database
            assertThat(userRepository.findByExternalId(TestSecurityConfig.TEST_EXTERNAL_ID)).isPresent();
        }

        @Test
        @DisplayName("should return existing user if already synced")
        void shouldReturnExistingUserIfAlreadySynced() throws Exception {
            mockMvc.perform(get("/api/users/me")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(existingUser.getExternalId())
                                            .claim("email", existingUser.getEmail())
                                            .claim("preferred_username", existingUser.getDisplayName()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(existingUser.getId().toString())))
                    .andExpect(jsonPath("$.displayName", is(existingUser.getDisplayName())));
        }

        @Test
        @DisplayName("should return 401 without authentication")
        void shouldReturn401WithoutAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserProfile {

        @Test
        @DisplayName("should return user profile by database ID")
        void shouldReturnUserProfileByDatabaseId() throws Exception {
            mockMvc.perform(get("/api/users/" + existingUser.getId())
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(existingUser.getId().toString())))
                    .andExpect(jsonPath("$.displayName", is(existingUser.getDisplayName())));
        }

        @Test
        @DisplayName("should return 404 for non-existent user")
        void shouldReturn404ForNonExistentUser() throws Exception {
            mockMvc.perform(get("/api/users/" + UUID.randomUUID())
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for user with allowMatching=false")
        void shouldReturn404ForNonMatchableUser() throws Exception {
            existingUser.setAllowMatching(false);
            userRepository.save(existingUser);

            mockMvc.perform(get("/api/users/" + existingUser.getId())
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for deleted user")
        void shouldReturn404ForDeletedUser() throws Exception {
            existingUser.softDeleteNow();
            userRepository.save(existingUser);

            mockMvc.perform(get("/api/users/" + existingUser.getId())
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("HEAD /api/users/{id}/exists")
    class UserExists {

        @Test
        @DisplayName("should return 200 for existing active user")
        void shouldReturn200ForExistingActiveUser() throws Exception {
            mockMvc.perform(head("/api/users/" + existingUser.getId() + "/exists")
                            .with(jwt().jwt(builder -> builder
                                    .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                    .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 for non-existent user")
        void shouldReturn404ForNonExistentUser() throws Exception {
            mockMvc.perform(head("/api/users/" + UUID.randomUUID() + "/exists")
                            .with(jwt().jwt(builder -> builder
                                    .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                    .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for inactive user")
        void shouldReturn404ForInactiveUser() throws Exception {
            existingUser.setActive(false);
            userRepository.save(existingUser);

            mockMvc.perform(head("/api/users/" + existingUser.getId() + "/exists")
                            .with(jwt().jwt(builder -> builder
                                    .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                    .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for deleted user")
        void shouldReturn404ForDeletedUser() throws Exception {
            existingUser.softDeleteNow();
            userRepository.save(existingUser);

            mockMvc.perform(head("/api/users/" + existingUser.getId() + "/exists")
                            .with(jwt().jwt(builder -> builder
                                    .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                    .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/profile")
    class UpdateProfile {

        @Test
        @DisplayName("should update user profile")
        void shouldUpdateUserProfile() throws Exception {
            mockMvc.perform(put("/api/users/me/profile")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(existingUser.getExternalId())
                                            .claim("email", existingUser.getEmail())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "displayName": "Updated Name",
                                        "bio": "My new bio",
                                        "timeZone": "Europe/Amsterdam"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName", is("Updated Name")))
                    .andExpect(jsonPath("$.bio", is("My new bio")))
                    .andExpect(jsonPath("$.timeZone", is("Europe/Amsterdam")));

            // Verify database was updated
            User updated = userRepository.findById(existingUser.getId()).orElseThrow();
            assertThat(updated.getDisplayName()).isEqualTo("Updated Name");
            assertThat(updated.getBio()).isEqualTo("My new bio");
        }

        @Test
        @DisplayName("should sanitize XSS in display name")
        void shouldSanitizeXssInDisplayName() throws Exception {
            mockMvc.perform(put("/api/users/me/profile")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(existingUser.getExternalId())
                                            .claim("email", existingUser.getEmail())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "displayName": "<script>alert('xss')</script>Safe Name"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName", not(containsString("<script>"))));
        }

        @Test
        @DisplayName("should return 400 for empty display name")
        void shouldReturn400ForEmptyDisplayName() throws Exception {
            mockMvc.perform(put("/api/users/me/profile")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(existingUser.getExternalId())
                                            .claim("email", existingUser.getEmail())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "displayName": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/preferences")
    class UpdatePreferences {

        @Test
        @DisplayName("should update user preferences")
        void shouldUpdateUserPreferences() throws Exception {
            mockMvc.perform(put("/api/users/me/preferences")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(existingUser.getExternalId())
                                            .claim("email", existingUser.getEmail())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "allowMatching": false,
                                        "allowEmails": false
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowMatching", is(false)))
                    .andExpect(jsonPath("$.allowEmails", is(false)));

            // Verify database was updated
            User updated = userRepository.findById(existingUser.getId()).orElseThrow();
            assertThat(updated.isAllowMatching()).isFalse();
            assertThat(updated.isAllowEmails()).isFalse();
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me")
    class DeleteMe {

        @Test
        @DisplayName("should soft delete user account")
        void shouldSoftDeleteUserAccount() throws Exception {
            mockMvc.perform(delete("/api/users/me")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(existingUser.getExternalId())
                                            .claim("email", existingUser.getEmail()))))
                    .andExpect(status().isOk());

            // Verify user is soft-deleted
            User deleted = userRepository.findById(existingUser.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("GET /api/users/resolve/{externalId}")
    class ResolveId {

        @Test
        @DisplayName("should resolve external ID to database ID")
        void shouldResolveExternalIdToDatabaseId() throws Exception {
            mockMvc.perform(get("/api/users/resolve/" + existingUser.getExternalId())
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.databaseId", is(existingUser.getId().toString())))
                    .andExpect(jsonPath("$.externalId", is(existingUser.getExternalId())))
                    .andExpect(jsonPath("$.displayName", is(existingUser.getDisplayName())));
        }

        @Test
        @DisplayName("should return 404 for non-existent external ID")
        void shouldReturn404ForNonExistentExternalId() throws Exception {
            mockMvc.perform(get("/api/users/resolve/non-existent-ext-id")
                            .with(jwt()
                                    .jwt(builder -> builder
                                            .subject(TestSecurityConfig.TEST_EXTERNAL_ID)
                                            .claim("email", TestSecurityConfig.TEST_EMAIL))))
                    .andExpect(status().isNotFound());
        }
    }
}
