package nl.ak.skillswap.userservice.integration;

import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full user journey integration test.
 * Tests the complete flow from user registration to profile updates.
 */
@DisplayName("User Journey Integration Tests")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class UserJourneyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Full user journey: registration → profile update → preferences update → view profile → delete")
    void fullUserJourney() throws Exception {
        String externalId1 = "journey-user-1-" + UUID.randomUUID();
        String email1 = "user1@journey.test";
        String username1 = "JourneyUser1";

        String externalId2 = "journey-user-2-" + UUID.randomUUID();
        String email2 = "user2@journey.test";
        String username2 = "JourneyUser2";

        // Step 1: First user calls /me - creates account automatically
        MvcResult user1Result = mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId1)
                                .claim("email", email1)
                                .claim("preferred_username", username1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId", is(externalId1)))
                .andExpect(jsonPath("$.email", is(email1)))
                .andExpect(jsonPath("$.displayName", is(username1)))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.allowMatching", is(true)))
                .andReturn();

        // Extract user1 ID from response
        String user1IdString = com.jayway.jsonpath.JsonPath.read(
                user1Result.getResponse().getContentAsString(), "$.id");
        UUID user1Id = UUID.fromString(user1IdString);

        // Step 2: Second user calls /me - creates account
        MvcResult user2Result = mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId2)
                                .claim("email", email2)
                                .claim("preferred_username", username2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId", is(externalId2)))
                .andReturn();

        // Step 3: User 1 updates profile
        mockMvc.perform(put("/api/users/me/profile")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId1)
                                .claim("email", email1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "displayName": "Journey User One",
                                    "bio": "I love skill swapping!",
                                    "timeZone": "Europe/London"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Journey User One")))
                .andExpect(jsonPath("$.bio", is("I love skill swapping!")))
                .andExpect(jsonPath("$.timeZone", is("Europe/London")));

        // Step 4: User 2 views User 1's profile
        mockMvc.perform(get("/api/users/" + user1Id)
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId2)
                                .claim("email", email2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Journey User One")))
                .andExpect(jsonPath("$.bio", is("I love skill swapping!")));

        // Step 5: User 1 disables matching
        mockMvc.perform(put("/api/users/me/preferences")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId1)
                                .claim("email", email1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "allowMatching": false,
                                    "allowEmails": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowMatching", is(false)));

        // Step 6: User 2 tries to view User 1's profile - should get 404 (allowMatching is false)
        mockMvc.perform(get("/api/users/" + user1Id)
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId2)
                                .claim("email", email2))))
                .andExpect(status().isNotFound());

        // Step 7: User 1 deletes account
        mockMvc.perform(delete("/api/users/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId1)
                                .claim("email", email1))))
                .andExpect(status().isOk());

        // Step 8: Verify User 1 is soft-deleted
        User deletedUser = userRepository.findById(user1Id).orElseThrow();
        assertThat(deletedUser.getDeletedAt()).isNotNull();

        // Step 9: User exists check should return 404
        mockMvc.perform(head("/api/users/" + user1Id + "/exists")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId2)
                                .claim("email", email2))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ID resolution flow for service-to-service calls")
    void idResolutionFlow() throws Exception {
        String externalId = "internal-test-" + UUID.randomUUID();
        String email = "internal@test.com";
        String username = "InternalTestUser";

        // Step 1: Create user via /me
        MvcResult userResult = mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId)
                                .claim("email", email)
                                .claim("preferred_username", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is(username)))
                .andReturn();

        String userIdString = com.jayway.jsonpath.JsonPath.read(
                userResult.getResponse().getContentAsString(), "$.id");

        // Step 2: Resolve external ID to database ID
        // Note: The resolve endpoint includes displayName to sync from Keycloak, which updates it
        mockMvc.perform(get("/api/users/resolve/" + externalId)
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId)
                                .claim("email", email)
                                .claim("preferred_username", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseId", is(userIdString)))
                .andExpect(jsonPath("$.externalId", is(externalId)))
                .andExpect(jsonPath("$.displayName", notNullValue()));

        // Step 3: Check user exists via HEAD request
        mockMvc.perform(head("/api/users/" + userIdString + "/exists")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId)
                                .claim("email", email))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User sync updates displayName from Keycloak claims")
    void userSyncUpdatesDisplayName() throws Exception {
        String externalId = "sync-test-" + UUID.randomUUID();
        String email = "sync@test.com";
        String originalName = "Original Name";
        String updatedName = "Updated Name";

        // Step 1: Create user with original name
        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId)
                                .claim("email", email)
                                .claim("preferred_username", originalName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is(originalName)));

        // Step 2: Login again with updated name - should sync
        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(externalId)
                                .claim("email", email)
                                .claim("preferred_username", updatedName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is(updatedName)));

        // Verify database was updated
        User user = userRepository.findByExternalId(externalId).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo(updatedName);
    }
}
