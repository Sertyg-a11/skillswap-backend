package nl.ak.skillswap.messageservice.integration;

import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import nl.ak.skillswap.messageservice.support.UserContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full messaging journey integration test.
 * Tests the complete flow from starting a conversation to reading messages.
 */
@DisplayName("Messaging Journey Integration Tests")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class MessagingJourneyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserContextResolver userContextResolver;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final UUID aliceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final String aliceExternalId = "alice-keycloak-sub";

    private final UUID bobId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final String bobExternalId = "bob-keycloak-sub";

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        // Clear Redis unread counters
        var keys = redisTemplate.keys("unread:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void loginAsAlice() {
        when(userContextResolver.resolve(any())).thenReturn(new AuthenticatedUserContext(
                aliceId, aliceExternalId, "Bearer alice-token"
        ));
    }

    private void loginAsBob() {
        when(userContextResolver.resolve(any())).thenReturn(new AuthenticatedUserContext(
                bobId, bobExternalId, "Bearer bob-token"
        ));
    }

    @Test
    @DisplayName("Full messaging journey: Alice and Bob exchange messages")
    void fullMessagingJourney() throws Exception {
        // Step 1: Alice starts a conversation with Bob
        loginAsAlice();

        MvcResult message1Result = mockMvc.perform(post("/api/messages/to/" + bobId)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Hi Bob! Want to swap skills?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body", is("Hi Bob! Want to swap skills?")))
                .andExpect(jsonPath("$.senderId", is(aliceId.toString())))
                .andExpect(jsonPath("$.recipientId", is(bobId.toString())))
                .andExpect(jsonPath("$.conversationId", notNullValue()))
                .andReturn();

        String conversationIdString = com.jayway.jsonpath.JsonPath.read(
                message1Result.getResponse().getContentAsString(), "$.conversationId");
        UUID conversationId = UUID.fromString(conversationIdString);

        // Step 2: Alice's conversation list shows the new conversation
        mockMvc.perform(get("/api/conversations")
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(conversationId.toString())))
                .andExpect(jsonPath("$[0].otherUserId", is(bobId.toString())))
                .andExpect(jsonPath("$[0].unreadCount", is(0))); // Alice's own message, no unread

        // Step 3: Bob replies to Alice
        loginAsBob();

        mockMvc.perform(post("/api/messages/to/" + aliceId)
                        .with(jwt().jwt(builder -> builder.subject(bobExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Sure Alice! What skills do you have?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId", is(conversationId.toString()))) // Same conversation
                .andExpect(jsonPath("$.senderId", is(bobId.toString())))
                .andExpect(jsonPath("$.recipientId", is(aliceId.toString())));

        // Step 4: Bob sends another message
        mockMvc.perform(post("/api/messages/to/" + aliceId)
                        .with(jwt().jwt(builder -> builder.subject(bobExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "I can teach Python!"
                                }
                                """))
                .andExpect(status().isOk());

        // Step 5: Alice checks her conversations - should see 2 unread messages
        loginAsAlice();

        mockMvc.perform(get("/api/conversations")
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].unreadCount", is(2)));

        // Step 6: Alice lists messages in the conversation
        mockMvc.perform(get("/api/messages/conversation/" + conversationId)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.hasMore", is(false)))
                // Messages should be in reverse chronological order
                .andExpect(jsonPath("$.items[0].body", is("I can teach Python!")))
                .andExpect(jsonPath("$.items[1].body", is("Sure Alice! What skills do you have?")))
                .andExpect(jsonPath("$.items[2].body", is("Hi Bob! Want to swap skills?")));

        // Step 7: Alice marks conversation as read
        mockMvc.perform(post("/api/messages/conversation/" + conversationId + "/read")
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(content().string("2")); // 2 messages marked as read

        // Step 8: Alice's conversation now shows 0 unread
        mockMvc.perform(get("/api/conversations")
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount", is(0)));

        // Step 9: Alice replies
        mockMvc.perform(post("/api/messages/to/" + bobId)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Great! I can teach JavaScript. When are you free?"
                                }
                                """))
                .andExpect(status().isOk());

        // Step 10: Bob's conversation shows 2 unread (Alice's first message + Alice's reply)
        // Bob never read any messages, so both of Alice's messages to him are unread
        loginAsBob();

        mockMvc.perform(get("/api/conversations")
                        .with(jwt().jwt(builder -> builder.subject(bobExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].otherUserId", is(aliceId.toString())))
                .andExpect(jsonPath("$[0].unreadCount", is(2)));

        // Verify database state
        assertThat(conversationRepository.count()).isEqualTo(1);
        assertThat(messageRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Pagination: retrieve messages in pages")
    void paginationJourney() throws Exception {
        loginAsAlice();

        // Step 1: Alice sends 10 messages to Bob
        String conversationId = null;
        for (int i = 1; i <= 10; i++) {
            MvcResult result = mockMvc.perform(post("/api/messages/to/" + bobId)
                            .with(jwt().jwt(builder -> builder.subject(aliceExternalId)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {
                                        "body": "Message %d"
                                    }
                                    """, i)))
                    .andExpect(status().isOk())
                    .andReturn();

            if (conversationId == null) {
                conversationId = com.jayway.jsonpath.JsonPath.read(
                        result.getResponse().getContentAsString(), "$.conversationId");
            }
        }

        // Step 2: Get first page (3 items)
        MvcResult page1 = mockMvc.perform(get("/api/messages/conversation/" + conversationId)
                        .param("size", "3")
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.hasMore", is(true)))
                .andExpect(jsonPath("$.items[0].body", is("Message 10")))
                .andExpect(jsonPath("$.items[1].body", is("Message 9")))
                .andExpect(jsonPath("$.items[2].body", is("Message 8")))
                .andReturn();

        // Step 3: Get second page using cursor
        String lastMessageCreatedAt = com.jayway.jsonpath.JsonPath.read(
                page1.getResponse().getContentAsString(), "$.items[2].createdAt");

        mockMvc.perform(get("/api/messages/conversation/" + conversationId)
                        .param("size", "3")
                        .param("before", lastMessageCreatedAt)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.hasMore", is(true)))
                .andExpect(jsonPath("$.items[0].body", is("Message 7")))
                .andExpect(jsonPath("$.items[1].body", is("Message 6")))
                .andExpect(jsonPath("$.items[2].body", is("Message 5")));
    }

    @Test
    @DisplayName("Multiple conversations: user has conversations with different people")
    void multipleConversationsJourney() throws Exception {
        UUID charlieId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID daveId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        loginAsAlice();

        // Alice starts conversations with Bob, Charlie, and Dave
        mockMvc.perform(post("/api/messages/to/" + bobId)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Hi Bob!"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/messages/to/" + charlieId)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Hi Charlie!"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/messages/to/" + daveId)
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Hi Dave!"}
                                """))
                .andExpect(status().isOk());

        // Alice should see 3 conversations
        mockMvc.perform(get("/api/conversations")
                        .with(jwt().jwt(builder -> builder.subject(aliceExternalId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Verify database
        assertThat(conversationRepository.count()).isEqualTo(3);
        assertThat(messageRepository.count()).isEqualTo(3);
    }
}
