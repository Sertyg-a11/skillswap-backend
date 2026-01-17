package nl.ak.skillswap.messageservice.integration;

import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import nl.ak.skillswap.messageservice.support.UserContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MessageController Integration Tests")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class MessageControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserContextResolver userContextResolver;

    private Conversation conversation;
    private Message message1;
    private Message message2;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        // Setup mock to return test user
        when(userContextResolver.resolve(any())).thenReturn(new AuthenticatedUserContext(
                TestSecurityConfig.TEST_USER_DATABASE_ID,
                TestSecurityConfig.TEST_EXTERNAL_ID,
                "Bearer test-token"
        ));

        // Create a conversation between test users
        UUID lowId = TestSecurityConfig.TEST_USER_DATABASE_ID.compareTo(TestSecurityConfig.TEST_USER_2_DATABASE_ID) < 0
                ? TestSecurityConfig.TEST_USER_DATABASE_ID : TestSecurityConfig.TEST_USER_2_DATABASE_ID;
        UUID highId = TestSecurityConfig.TEST_USER_DATABASE_ID.compareTo(TestSecurityConfig.TEST_USER_2_DATABASE_ID) < 0
                ? TestSecurityConfig.TEST_USER_2_DATABASE_ID : TestSecurityConfig.TEST_USER_DATABASE_ID;

        conversation = conversationRepository.save(Conversation.builder()
                .id(UUID.randomUUID())
                .userLowId(lowId)
                .userHighId(highId)
                .createdAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build());

        Instant now = Instant.now();

        // Create test messages
        message1 = messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .senderId(TestSecurityConfig.TEST_USER_DATABASE_ID)
                .recipientId(TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                .body("Hello from user 1")
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        message2 = messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .senderId(TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                .recipientId(TestSecurityConfig.TEST_USER_DATABASE_ID)
                .body("Hello from user 2")
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build());
    }

    @Nested
    @DisplayName("POST /api/messages/to/{otherUserId}")
    class SendMessage {

        @Test
        @DisplayName("should send message to another user")
        void shouldSendMessageToAnotherUser() throws Exception {
            mockMvc.perform(post("/api/messages/to/" + TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "body": "Hello, this is a test message!"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body", is("Hello, this is a test message!")))
                    .andExpect(jsonPath("$.senderId", is(TestSecurityConfig.TEST_USER_DATABASE_ID.toString())))
                    .andExpect(jsonPath("$.recipientId", is(TestSecurityConfig.TEST_USER_2_DATABASE_ID.toString())))
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.conversationId", notNullValue()))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));

            // Verify message was saved
            long messageCount = messageRepository.countBySenderId(TestSecurityConfig.TEST_USER_DATABASE_ID);
            assertThat(messageCount).isEqualTo(2); // 1 from setup + 1 new
        }

        @Test
        @DisplayName("should create new conversation if none exists")
        void shouldCreateNewConversationIfNoneExists() throws Exception {
            UUID newUserId = UUID.randomUUID();

            mockMvc.perform(post("/api/messages/to/" + newUserId)
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "body": "First message to new user"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId", notNullValue()));

            // Verify new conversation was created
            long conversationCount = conversationRepository.count();
            assertThat(conversationCount).isEqualTo(2); // 1 from setup + 1 new
        }

        @Test
        @DisplayName("should return 400 for empty message body")
        void shouldReturn400ForEmptyMessageBody() throws Exception {
            mockMvc.perform(post("/api/messages/to/" + TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "body": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 without authentication")
        void shouldReturn401WithoutAuthentication() throws Exception {
            mockMvc.perform(post("/api/messages/to/" + TestSecurityConfig.TEST_USER_2_DATABASE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "body": "Test message"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/messages/conversation/{conversationId}")
    class ListMessages {

        @Test
        @DisplayName("should list messages in conversation")
        void shouldListMessagesInConversation() throws Exception {
            mockMvc.perform(get("/api/messages/conversation/" + conversation.getId())
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.hasMore", is(false)));
        }

        @Test
        @DisplayName("should return messages in descending order by createdAt")
        void shouldReturnMessagesInDescendingOrder() throws Exception {
            mockMvc.perform(get("/api/messages/conversation/" + conversation.getId())
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id", is(message2.getId().toString())))
                    .andExpect(jsonPath("$.items[1].id", is(message1.getId().toString())));
        }

        @Test
        @DisplayName("should support pagination with size parameter")
        void shouldSupportPaginationWithSizeParameter() throws Exception {
            mockMvc.perform(get("/api/messages/conversation/" + conversation.getId())
                            .param("size", "1")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.hasMore", is(true)));
        }

        @Test
        @DisplayName("should support cursor pagination with before parameter")
        void shouldSupportCursorPagination() throws Exception {
            mockMvc.perform(get("/api/messages/conversation/" + conversation.getId())
                            .param("before", message2.getCreatedAt().toString())
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].id", is(message1.getId().toString())));
        }

        @Test
        @DisplayName("should return 404 for non-existent conversation")
        void shouldReturn404ForNonExistentConversation() throws Exception {
            // Non-existent conversation returns 404 (or 200 with empty list depending on implementation)
            mockMvc.perform(get("/api/messages/conversation/" + UUID.randomUUID())
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/messages/conversation/{conversationId}/read")
    class MarkRead {

        @Test
        @DisplayName("should mark messages as read")
        void shouldMarkMessagesAsRead() throws Exception {
            // User 1 marks messages from user 2 as read
            mockMvc.perform(post("/api/messages/conversation/" + conversation.getId() + "/read")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1")); // 1 message from user 2 to user 1

            // Verify message was marked as read
            Message readMessage = messageRepository.findById(message2.getId()).orElseThrow();
            assertThat(readMessage.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("should return 0 when no unread messages")
        void shouldReturn0WhenNoUnreadMessages() throws Exception {
            // Mark message as already read
            message2.setReadAt(Instant.now());
            messageRepository.save(message2);

            mockMvc.perform(post("/api/messages/conversation/" + conversation.getId() + "/read")
                            .with(jwt().jwt(builder -> builder.subject(TestSecurityConfig.TEST_EXTERNAL_ID))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }
    }
}
