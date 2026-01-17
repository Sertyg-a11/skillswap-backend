package nl.ak.skillswap.messageservice.integration;

import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import nl.ak.skillswap.messageservice.service.UnreadCounterService;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import nl.ak.skillswap.messageservice.support.UserContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ConversationController Integration Tests")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class ConversationControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserContextResolver userContextResolver;

    @Autowired
    private UnreadCounterService unreadCounterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Conversation conversation1;
    private Conversation conversation2;
    private UUID user3Id;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        // Clear Redis unread counters
        var keys = redisTemplate.keys("unread:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Setup mock to return test user
        when(userContextResolver.resolve(any())).thenReturn(new AuthenticatedUserContext(
                TestSecurityConfig.TEST_USER_DATABASE_ID,
                TestSecurityConfig.TEST_EXTERNAL_ID,
                "Bearer test-token"
        ));

        user3Id = UUID.randomUUID();

        // Create conversations with test user 1
        UUID lowId1 = TestSecurityConfig.TEST_USER_DATABASE_ID.compareTo(TestSecurityConfig.TEST_USER_2_DATABASE_ID) < 0
                ? TestSecurityConfig.TEST_USER_DATABASE_ID : TestSecurityConfig.TEST_USER_2_DATABASE_ID;
        UUID highId1 = TestSecurityConfig.TEST_USER_DATABASE_ID.compareTo(TestSecurityConfig.TEST_USER_2_DATABASE_ID) < 0
                ? TestSecurityConfig.TEST_USER_2_DATABASE_ID : TestSecurityConfig.TEST_USER_DATABASE_ID;

        UUID lowId2 = TestSecurityConfig.TEST_USER_DATABASE_ID.compareTo(user3Id) < 0
                ? TestSecurityConfig.TEST_USER_DATABASE_ID : user3Id;
        UUID highId2 = TestSecurityConfig.TEST_USER_DATABASE_ID.compareTo(user3Id) < 0
                ? user3Id : TestSecurityConfig.TEST_USER_DATABASE_ID;

        conversation1 = conversationRepository.save(Conversation.builder()
                .id(UUID.randomUUID())
                .userLowId(lowId1)
                .userHighId(highId1)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .lastMessageAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        conversation2 = conversationRepository.save(Conversation.builder()
                .id(UUID.randomUUID())
                .userLowId(lowId2)
                .userHighId(highId2)
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .lastMessageAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build());

        // Add some messages to conversation1
        messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation1.getId())
                .senderId(TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                .recipientId(TestSecurityConfig.TEST_USER_DATABASE_ID)
                .body("Unread message 1")
                .createdAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .build());

        messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation1.getId())
                .senderId(TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                .recipientId(TestSecurityConfig.TEST_USER_DATABASE_ID)
                .body("Unread message 2")
                .createdAt(Instant.now().minus(15, ChronoUnit.MINUTES))
                .build());

        // Set up Redis unread counters to match the messages
        unreadCounterService.incrementUnread(TestSecurityConfig.TEST_USER_DATABASE_ID, conversation1.getId());
        unreadCounterService.incrementUnread(TestSecurityConfig.TEST_USER_DATABASE_ID, conversation1.getId());
    }

    @Nested
    @DisplayName("GET /api/conversations")
    class GetMyConversations {

        @Test
        @DisplayName("should return all conversations for current user")
        void shouldReturnAllConversationsForCurrentUser() throws Exception {
            mockMvc.perform(get("/api/conversations")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("should return conversations ordered by lastMessageAt descending")
        void shouldReturnConversationsOrderedByLastMessageAt() throws Exception {
            mockMvc.perform(get("/api/conversations")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id", is(conversation1.getId().toString())))
                    .andExpect(jsonPath("$[1].id", is(conversation2.getId().toString())));
        }

        @Test
        @DisplayName("should include other participant ID")
        void shouldIncludeOtherParticipantId() throws Exception {
            mockMvc.perform(get("/api/conversations")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].otherUserId", notNullValue()))
                    .andExpect(jsonPath("$[1].otherUserId", notNullValue()));
        }

        @Test
        @DisplayName("should include unread count")
        void shouldIncludeUnreadCount() throws Exception {
            mockMvc.perform(get("/api/conversations")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].unreadCount", is(2)))  // 2 unread messages in conversation1
                    .andExpect(jsonPath("$[1].unreadCount", is(0))); // 0 messages in conversation2
        }

        @Test
        @DisplayName("should return empty list for user with no conversations")
        void shouldReturnEmptyListForUserWithNoConversations() throws Exception {
            UUID newUserId = UUID.randomUUID();
            when(userContextResolver.resolve(any())).thenReturn(new AuthenticatedUserContext(
                    newUserId,
                    "new-user-ext-id",
                    "Bearer test-token"
            ));

            mockMvc.perform(get("/api/conversations")
                            .with(jwt().jwt(builder -> builder.subject("new-user-ext-id"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("should return 401 without authentication")
        void shouldReturn401WithoutAuthentication() throws Exception {
            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
