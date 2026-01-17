package nl.ak.skillswap.messageservice.gdpr.service;

import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.gdpr.dto.GdprExportData;
import nl.ak.skillswap.messageservice.gdpr.event.GdprDeletionRequest;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageGdprService")
class MessageGdprServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private MessageGdprService messageGdprService;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("exportUserData")
    class ExportUserData {

        @Test
        @DisplayName("should export all user data")
        void shouldExportAllUserData() {
            UUID conversationId = UUID.randomUUID();

            // Ensure userId is "low" for the conversation
            UUID low = userId.compareTo(otherUserId) <= 0 ? userId : otherUserId;
            UUID high = userId.compareTo(otherUserId) <= 0 ? otherUserId : userId;

            Conversation conversation = Conversation.builder()
                    .id(conversationId)
                    .userLowId(low)
                    .userHighId(high)
                    .createdAt(Instant.now())
                    .lastMessageAt(Instant.now())
                    .build();

            Message sentMessage = Message.builder()
                    .id(UUID.randomUUID())
                    .conversationId(conversationId)
                    .senderId(userId)
                    .recipientId(otherUserId)
                    .body("Hello")
                    .createdAt(Instant.now())
                    .build();

            Message receivedMessage = Message.builder()
                    .id(UUID.randomUUID())
                    .conversationId(conversationId)
                    .senderId(otherUserId)
                    .recipientId(userId)
                    .body("Hi there")
                    .createdAt(Instant.now())
                    .readAt(Instant.now())
                    .build();

            when(conversationRepository.findAllByUserId(userId)).thenReturn(List.of(conversation));
            when(messageRepository.findBySenderId(userId)).thenReturn(List.of(sentMessage));
            when(messageRepository.findByRecipientId(userId)).thenReturn(List.of(receivedMessage));

            GdprExportData result = messageGdprService.exportUserData(userId);

            assertThat(result.serviceName()).isEqualTo("message-service");
            assertThat(result.exportedAt()).isNotNull();
            assertThat(result.conversations()).hasSize(1);
            assertThat(result.messagesSent()).hasSize(1);
            assertThat(result.messagesReceived()).hasSize(1);
            assertThat(result.summary().totalConversations()).isEqualTo(1);
            assertThat(result.summary().totalMessagesSent()).isEqualTo(1);
            assertThat(result.summary().totalMessagesReceived()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return empty data when user has no messages")
        void shouldReturnEmptyDataWhenNoMessages() {
            when(conversationRepository.findAllByUserId(userId)).thenReturn(List.of());
            when(messageRepository.findBySenderId(userId)).thenReturn(List.of());
            when(messageRepository.findByRecipientId(userId)).thenReturn(List.of());

            GdprExportData result = messageGdprService.exportUserData(userId);

            assertThat(result.conversations()).isEmpty();
            assertThat(result.messagesSent()).isEmpty();
            assertThat(result.messagesReceived()).isEmpty();
            assertThat(result.summary().totalConversations()).isEqualTo(0);
            assertThat(result.summary().totalMessagesSent()).isEqualTo(0);
            assertThat(result.summary().totalMessagesReceived()).isEqualTo(0);
        }

        @Test
        @DisplayName("should correctly identify sent vs received messages")
        void shouldCorrectlyIdentifySentVsReceivedMessages() {
            Message sentMessage = Message.builder()
                    .id(UUID.randomUUID())
                    .conversationId(UUID.randomUUID())
                    .senderId(userId)
                    .recipientId(otherUserId)
                    .body("Sent message")
                    .createdAt(Instant.now())
                    .build();

            Message receivedMessage = Message.builder()
                    .id(UUID.randomUUID())
                    .conversationId(UUID.randomUUID())
                    .senderId(otherUserId)
                    .recipientId(userId)
                    .body("Received message")
                    .createdAt(Instant.now())
                    .build();

            when(conversationRepository.findAllByUserId(userId)).thenReturn(List.of());
            when(messageRepository.findBySenderId(userId)).thenReturn(List.of(sentMessage));
            when(messageRepository.findByRecipientId(userId)).thenReturn(List.of(receivedMessage));

            GdprExportData result = messageGdprService.exportUserData(userId);

            assertThat(result.messagesSent()).hasSize(1);
            assertThat(result.messagesSent().get(0).isSent()).isTrue();
            assertThat(result.messagesSent().get(0).otherPartyId()).isEqualTo(otherUserId);

            assertThat(result.messagesReceived()).hasSize(1);
            assertThat(result.messagesReceived().get(0).isSent()).isFalse();
            assertThat(result.messagesReceived().get(0).otherPartyId()).isEqualTo(otherUserId);
        }
    }

    @Nested
    @DisplayName("deleteUserData")
    class DeleteUserData {

        @Test
        @DisplayName("should perform full deletion")
        void shouldPerformFullDeletion() {
            when(messageRepository.deleteMessagesByRecipient(userId)).thenReturn(5);
            when(messageRepository.anonymizeMessagesBySender(userId)).thenReturn(3);
            when(conversationRepository.deleteEmptyConversationsByUserId(userId)).thenReturn(2);

            MessageGdprService.DeletionResult result = messageGdprService.deleteUserData(
                    userId, GdprDeletionRequest.DeletionType.FULL);

            assertThat(result.success()).isTrue();
            assertThat(result.serviceName()).isEqualTo("message-service");
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.messagesDeleted()).isEqualTo(5);
            assertThat(result.messagesAnonymized()).isEqualTo(3);
            assertThat(result.conversationsDeleted()).isEqualTo(2);
            assertThat(result.errorMessage()).isNull();

            verify(messageRepository).deleteMessagesByRecipient(userId);
            verify(messageRepository).anonymizeMessagesBySender(userId);
            verify(conversationRepository).deleteEmptyConversationsByUserId(userId);
        }

        @Test
        @DisplayName("should perform anonymize deletion")
        void shouldPerformAnonymizeDeletion() {
            when(messageRepository.anonymizeMessagesBySender(userId)).thenReturn(3);
            when(messageRepository.deleteMessagesByRecipient(userId)).thenReturn(5);
            when(conversationRepository.deleteEmptyConversationsByUserId(userId)).thenReturn(1);

            MessageGdprService.DeletionResult result = messageGdprService.deleteUserData(
                    userId, GdprDeletionRequest.DeletionType.ANONYMIZE);

            assertThat(result.success()).isTrue();
            assertThat(result.messagesAnonymized()).isEqualTo(3);
            assertThat(result.messagesDeleted()).isEqualTo(5);
            assertThat(result.conversationsDeleted()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle case with no data to delete")
        void shouldHandleNoDataToDelete() {
            when(messageRepository.deleteMessagesByRecipient(userId)).thenReturn(0);
            when(messageRepository.anonymizeMessagesBySender(userId)).thenReturn(0);
            when(conversationRepository.deleteEmptyConversationsByUserId(userId)).thenReturn(0);

            MessageGdprService.DeletionResult result = messageGdprService.deleteUserData(
                    userId, GdprDeletionRequest.DeletionType.FULL);

            assertThat(result.success()).isTrue();
            assertThat(result.messagesDeleted()).isEqualTo(0);
            assertThat(result.messagesAnonymized()).isEqualTo(0);
            assertThat(result.conversationsDeleted()).isEqualTo(0);
        }

        @Test
        @DisplayName("should set deletedAt timestamp")
        void shouldSetDeletedAtTimestamp() {
            Instant before = Instant.now();

            when(messageRepository.deleteMessagesByRecipient(userId)).thenReturn(0);
            when(messageRepository.anonymizeMessagesBySender(userId)).thenReturn(0);
            when(conversationRepository.deleteEmptyConversationsByUserId(userId)).thenReturn(0);

            MessageGdprService.DeletionResult result = messageGdprService.deleteUserData(
                    userId, GdprDeletionRequest.DeletionType.FULL);

            assertThat(result.deletedAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("DeletionResult.error")
    class DeletionResultError {

        @Test
        @DisplayName("should create error result with correct values")
        void shouldCreateErrorResultWithCorrectValues() {
            String errorMessage = "Something went wrong";

            MessageGdprService.DeletionResult result = MessageGdprService.DeletionResult.error(userId, errorMessage);

            assertThat(result.success()).isFalse();
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.errorMessage()).isEqualTo(errorMessage);
            assertThat(result.messagesDeleted()).isEqualTo(0);
            assertThat(result.messagesAnonymized()).isEqualTo(0);
            assertThat(result.conversationsDeleted()).isEqualTo(0);
            assertThat(result.deletedAt()).isNotNull();
        }
    }
}
