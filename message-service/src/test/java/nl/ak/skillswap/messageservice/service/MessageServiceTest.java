package nl.ak.skillswap.messageservice.service;

import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import nl.ak.skillswap.messageservice.service.event.MessageCreatedEvent;
import nl.ak.skillswap.messageservice.service.event.MessageEventPublisher;
import nl.ak.skillswap.messageservice.support.ForbiddenException;
import nl.ak.skillswap.messageservice.support.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService")
class MessageServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UnreadCounterService unreadCounterService;

    @Mock
    private MessageEventPublisher eventPublisher;

    @Mock
    private UserValidationService userValidationService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private MessageSanitizer messageSanitizer;

    @Mock
    private RealTimeMessagingService realTimeMessagingService;

    @InjectMocks
    private MessageService messageService;

    private UUID senderId;
    private UUID recipientId;
    private UUID conversationId;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        // Ensure sender is "low" for consistent conversation creation
        UUID low = senderId.compareTo(recipientId) <= 0 ? senderId : recipientId;
        UUID high = senderId.compareTo(recipientId) <= 0 ? recipientId : senderId;

        conversation = Conversation.builder()
                .id(conversationId)
                .userLowId(low)
                .userHighId(high)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("should send message successfully")
        void shouldSendMessageSuccessfully() {
            String body = "Hello, world!";
            String sanitizedBody = "Hello, world!";

            doNothing().when(rateLimitingService).checkMessageRateLimit(senderId);
            when(userValidationService.canSendMessageTo(senderId, recipientId)).thenReturn(true);
            when(messageSanitizer.sanitize(body)).thenReturn(sanitizedBody);
            when(conversationService.getOrCreate(senderId, recipientId)).thenReturn(conversation);
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Message result = messageService.sendMessage(senderId, recipientId, body);

            assertThat(result.getSenderId()).isEqualTo(senderId);
            assertThat(result.getRecipientId()).isEqualTo(recipientId);
            assertThat(result.getBody()).isEqualTo(sanitizedBody);
            assertThat(result.getConversationId()).isEqualTo(conversationId);
            assertThat(result.getReadAt()).isNull();

            verify(conversationService).touchLastMessage(eq(conversation), any(Instant.class));
            verify(unreadCounterService).incrementUnread(recipientId, conversationId);
            verify(eventPublisher).publishMessageCreated(any(MessageCreatedEvent.class));
        }

        @Test
        @DisplayName("should throw when rate limit exceeded")
        void shouldThrowWhenRateLimitExceeded() {
            doThrow(new RateLimitExceededException("Rate limit exceeded"))
                    .when(rateLimitingService).checkMessageRateLimit(senderId);

            assertThatThrownBy(() -> messageService.sendMessage(senderId, recipientId, "test"))
                    .isInstanceOf(RateLimitExceededException.class);

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when user cannot send to recipient")
        void shouldThrowWhenCannotSendToRecipient() {
            doNothing().when(rateLimitingService).checkMessageRateLimit(senderId);
            doThrow(new RuntimeException("User not found"))
                    .when(userValidationService).canSendMessageTo(senderId, recipientId);

            assertThatThrownBy(() -> messageService.sendMessage(senderId, recipientId, "test"))
                    .isInstanceOf(RuntimeException.class);

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ForbiddenException when user not in conversation")
        void shouldThrowForbiddenWhenUserNotInConversation() {
            UUID otherUser1 = UUID.randomUUID();
            UUID otherUser2 = UUID.randomUUID();
            Conversation otherConversation = Conversation.builder()
                    .id(UUID.randomUUID())
                    .userLowId(otherUser1)
                    .userHighId(otherUser2)
                    .createdAt(Instant.now())
                    .build();

            doNothing().when(rateLimitingService).checkMessageRateLimit(senderId);
            when(userValidationService.canSendMessageTo(senderId, recipientId)).thenReturn(true);
            when(messageSanitizer.sanitize(any())).thenReturn("test");
            when(conversationService.getOrCreate(senderId, recipientId)).thenReturn(otherConversation);

            assertThatThrownBy(() -> messageService.sendMessage(senderId, recipientId, "test"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Not allowed");
        }

        @Test
        @DisplayName("should publish message created event with correct data")
        void shouldPublishEventWithCorrectData() {
            doNothing().when(rateLimitingService).checkMessageRateLimit(senderId);
            when(userValidationService.canSendMessageTo(senderId, recipientId)).thenReturn(true);
            when(messageSanitizer.sanitize(any())).thenReturn("test");
            when(conversationService.getOrCreate(senderId, recipientId)).thenReturn(conversation);
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

            messageService.sendMessage(senderId, recipientId, "test");

            ArgumentCaptor<MessageCreatedEvent> eventCaptor = ArgumentCaptor.forClass(MessageCreatedEvent.class);
            verify(eventPublisher).publishMessageCreated(eventCaptor.capture());

            MessageCreatedEvent event = eventCaptor.getValue();
            assertThat(event.senderId()).isEqualTo(senderId);
            assertThat(event.recipientId()).isEqualTo(recipientId);
            assertThat(event.conversationId()).isEqualTo(conversationId);
        }
    }

    @Nested
    @DisplayName("listMessages")
    class ListMessages {

        @Test
        @DisplayName("should list messages for conversation")
        void shouldListMessagesForConversation() {
            Message message1 = Message.builder().id(UUID.randomUUID()).body("msg1").build();
            Message message2 = Message.builder().id(UUID.randomUUID()).body("msg2").build();

            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);
            when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(conversationId), any(PageRequest.class)))
                    .thenReturn(List.of(message1, message2));

            List<Message> result = messageService.listMessages(senderId, conversationId, null, 20);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(message1, message2);
        }

        @Test
        @DisplayName("should use cursor pagination when before timestamp provided")
        void shouldUseCursorPaginationWhenBeforeProvided() {
            Instant before = Instant.now();

            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);
            when(messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    eq(conversationId), eq(before), any(PageRequest.class)))
                    .thenReturn(List.of());

            messageService.listMessages(senderId, conversationId, before, 20);

            verify(messageRepository).findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    eq(conversationId), eq(before), any(PageRequest.class));
        }

        @Test
        @DisplayName("should clamp page size to 1-100 range")
        void shouldClampPageSize() {
            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);
            when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(conversationId), any(PageRequest.class)))
                    .thenReturn(List.of());

            // Test minimum clamping
            messageService.listMessages(senderId, conversationId, null, -5);
            ArgumentCaptor<PageRequest> captor1 = ArgumentCaptor.forClass(PageRequest.class);
            verify(messageRepository).findByConversationIdOrderByCreatedAtDesc(eq(conversationId), captor1.capture());
            assertThat(captor1.getValue().getPageSize()).isEqualTo(1);

            // Test maximum clamping
            reset(messageRepository);
            when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(conversationId), any(PageRequest.class)))
                    .thenReturn(List.of());
            messageService.listMessages(senderId, conversationId, null, 500);
            ArgumentCaptor<PageRequest> captor2 = ArgumentCaptor.forClass(PageRequest.class);
            verify(messageRepository).findByConversationIdOrderByCreatedAtDesc(eq(conversationId), captor2.capture());
            assertThat(captor2.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("should throw ForbiddenException when user not in conversation")
        void shouldThrowForbiddenWhenUserNotInConversation() {
            UUID outsider = UUID.randomUUID();
            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);

            assertThatThrownBy(() -> messageService.listMessages(outsider, conversationId, null, 20))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Not allowed");
        }
    }

    @Nested
    @DisplayName("markRead")
    class MarkRead {

        @Test
        @DisplayName("should mark messages as read and notify sender")
        void shouldMarkMessagesAsReadAndNotifySender() {
            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);
            when(messageRepository.markConversationRead(eq(conversationId), eq(senderId), any(Instant.class)))
                    .thenReturn(5);

            int result = messageService.markRead(senderId, conversationId);

            assertThat(result).isEqualTo(5);
            verify(unreadCounterService).clearUnread(senderId, conversationId);
            verify(realTimeMessagingService).notifyMessagesRead(recipientId, conversationId);
        }

        @Test
        @DisplayName("should not notify if no messages were updated")
        void shouldNotNotifyIfNoMessagesUpdated() {
            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);
            when(messageRepository.markConversationRead(eq(conversationId), eq(senderId), any(Instant.class)))
                    .thenReturn(0);

            int result = messageService.markRead(senderId, conversationId);

            assertThat(result).isEqualTo(0);
            verify(unreadCounterService).clearUnread(senderId, conversationId);
            verify(realTimeMessagingService, never()).notifyMessagesRead(any(), any());
        }

        @Test
        @DisplayName("should throw ForbiddenException when user not in conversation")
        void shouldThrowForbiddenWhenUserNotInConversation() {
            UUID outsider = UUID.randomUUID();
            when(conversationService.getOrThrow(conversationId)).thenReturn(conversation);

            assertThatThrownBy(() -> messageService.markRead(outsider, conversationId))
                    .isInstanceOf(ForbiddenException.class);

            verify(messageRepository, never()).markConversationRead(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("unreadCount")
    class UnreadCount {

        @Test
        @DisplayName("should return unread count from service")
        void shouldReturnUnreadCount() {
            when(unreadCounterService.getUnread(senderId, conversationId)).thenReturn(10L);

            long result = messageService.unreadCount(senderId, conversationId);

            assertThat(result).isEqualTo(10L);
        }
    }
}
