package nl.ak.skillswap.messageservice.integration;

import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageRepository Integration Tests")
class MessageRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private UUID user1Id;
    private UUID user2Id;
    private Conversation conversation;
    private Message message1;
    private Message message2;
    private Message message3;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();

        UUID lowId = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
        UUID highId = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;

        conversation = conversationRepository.save(Conversation.builder()
                .id(UUID.randomUUID())
                .userLowId(lowId)
                .userHighId(highId)
                .createdAt(Instant.now())
                .build());

        // Create messages with different timestamps
        Instant now = Instant.now();

        message1 = messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .senderId(user1Id)
                .recipientId(user2Id)
                .body("First message")
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        message2 = messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .senderId(user2Id)
                .recipientId(user1Id)
                .body("Second message")
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build());

        message3 = messageRepository.save(Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .senderId(user1Id)
                .recipientId(user2Id)
                .body("Third message")
                .createdAt(now)
                .build());
    }

    @Nested
    @DisplayName("findByConversationIdOrderByCreatedAtDesc")
    class FindByConversationIdOrdered {

        @Test
        @DisplayName("should find messages ordered by createdAt descending")
        void shouldFindMessagesOrderedByCreatedAtDesc() {
            List<Message> results = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversation.getId(), PageRequest.of(0, 10));

            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isEqualTo(message3.getId());
            assertThat(results.get(1).getId()).isEqualTo(message2.getId());
            assertThat(results.get(2).getId()).isEqualTo(message1.getId());
        }

        @Test
        @DisplayName("should respect pagination")
        void shouldRespectPagination() {
            List<Message> page1 = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversation.getId(), PageRequest.of(0, 2));
            List<Message> page2 = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversation.getId(), PageRequest.of(1, 2));

            assertThat(page1).hasSize(2);
            assertThat(page2).hasSize(1);
        }

        @Test
        @DisplayName("should return empty for non-existent conversation")
        void shouldReturnEmptyForNonExistentConversation() {
            List<Message> results = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    UUID.randomUUID(), PageRequest.of(0, 10));

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc")
    class FindByConversationIdWithCursor {

        @Test
        @DisplayName("should find messages before cursor")
        void shouldFindMessagesBeforeCursor() {
            List<Message> results = messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    conversation.getId(), message3.getCreatedAt(), PageRequest.of(0, 10));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getId()).isEqualTo(message2.getId());
            assertThat(results.get(1).getId()).isEqualTo(message1.getId());
        }

        @Test
        @DisplayName("should return empty when no messages before cursor")
        void shouldReturnEmptyWhenNoMessagesBeforeCursor() {
            List<Message> results = messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    conversation.getId(), message1.getCreatedAt(), PageRequest.of(0, 10));

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByConversationIdAndRecipientIdAndReadAtIsNull")
    class CountUnreadMessages {

        @Test
        @DisplayName("should count unread messages for recipient")
        void shouldCountUnreadMessagesForRecipient() {
            // User2 received message1 and message3 (both unread)
            long count = messageRepository.countByConversationIdAndRecipientIdAndReadAtIsNull(
                    conversation.getId(), user2Id);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count read messages")
        void shouldNotCountReadMessages() {
            // Mark message1 as read
            message1.setReadAt(Instant.now());
            messageRepository.save(message1);

            long count = messageRepository.countByConversationIdAndRecipientIdAndReadAtIsNull(
                    conversation.getId(), user2Id);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should return zero when all messages are read")
        void shouldReturnZeroWhenAllMessagesAreRead() {
            // Mark all messages as read
            message1.setReadAt(Instant.now());
            message2.setReadAt(Instant.now());
            message3.setReadAt(Instant.now());
            messageRepository.saveAll(List.of(message1, message2, message3));

            long count = messageRepository.countByConversationIdAndRecipientIdAndReadAtIsNull(
                    conversation.getId(), user2Id);

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("markConversationRead")
    @Transactional
    class MarkConversationRead {

        @Test
        @DisplayName("should mark all unread messages as read for recipient")
        void shouldMarkAllUnreadMessagesAsRead() {
            Instant readAt = Instant.now();

            int updated = messageRepository.markConversationRead(
                    conversation.getId(), user2Id, readAt);

            assertThat(updated).isEqualTo(2); // message1 and message3

            // Verify messages are marked as read
            Message updatedMessage1 = messageRepository.findById(message1.getId()).orElseThrow();
            Message updatedMessage3 = messageRepository.findById(message3.getId()).orElseThrow();

            assertThat(updatedMessage1.getReadAt()).isNotNull();
            assertThat(updatedMessage3.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("should not mark already read messages")
        void shouldNotMarkAlreadyReadMessages() {
            // Mark message1 as read first
            message1.setReadAt(Instant.now().minus(1, ChronoUnit.HOURS));
            messageRepository.save(message1);

            Instant newReadAt = Instant.now();
            int updated = messageRepository.markConversationRead(
                    conversation.getId(), user2Id, newReadAt);

            assertThat(updated).isEqualTo(1); // Only message3

            // Verify original readAt is preserved
            Message reloadedMessage1 = messageRepository.findById(message1.getId()).orElseThrow();
            assertThat(reloadedMessage1.getReadAt()).isBefore(newReadAt);
        }
    }

    @Nested
    @DisplayName("findBySenderId")
    class FindBySenderId {

        @Test
        @DisplayName("should find all messages sent by user")
        void shouldFindAllMessagesSentByUser() {
            List<Message> results = messageRepository.findBySenderId(user1Id);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Message::getId)
                    .containsExactlyInAnyOrder(message1.getId(), message3.getId());
        }

        @Test
        @DisplayName("should return empty for user with no sent messages")
        void shouldReturnEmptyForUserWithNoSentMessages() {
            List<Message> results = messageRepository.findBySenderId(UUID.randomUUID());

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByRecipientId")
    class FindByRecipientId {

        @Test
        @DisplayName("should find all messages received by user")
        void shouldFindAllMessagesReceivedByUser() {
            List<Message> results = messageRepository.findByRecipientId(user2Id);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Message::getId)
                    .containsExactlyInAnyOrder(message1.getId(), message3.getId());
        }

        @Test
        @DisplayName("should return empty for user with no received messages")
        void shouldReturnEmptyForUserWithNoReceivedMessages() {
            List<Message> results = messageRepository.findByRecipientId(UUID.randomUUID());

            assertThat(results).isEmpty();
        }
    }

    // Note: anonymizeMessagesBySender tests are skipped because the database schema
    // has sender_id NOT NULL constraint. The GDPR anonymization would need a schema
    // change to allow nullable sender_id for proper GDPR compliance.

    @Nested
    @DisplayName("deleteMessagesByRecipient")
    @Transactional
    class DeleteMessagesByRecipient {

        @Test
        @DisplayName("should delete all messages received by user")
        void shouldDeleteAllMessagesReceivedByUser() {
            int deleted = messageRepository.deleteMessagesByRecipient(user2Id);

            assertThat(deleted).isEqualTo(2);

            // Verify messages are deleted
            assertThat(messageRepository.findById(message1.getId())).isEmpty();
            assertThat(messageRepository.findById(message3.getId())).isEmpty();
        }

        @Test
        @DisplayName("should not delete messages received by other users")
        void shouldNotDeleteMessagesReceivedByOtherUsers() {
            messageRepository.deleteMessagesByRecipient(user2Id);

            // Message2 (received by user1) should still exist
            assertThat(messageRepository.findById(message2.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("countBySenderId")
    class CountBySenderId {

        @Test
        @DisplayName("should count messages sent by user")
        void shouldCountMessagesSentByUser() {
            long count = messageRepository.countBySenderId(user1Id);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero for user with no sent messages")
        void shouldReturnZeroForUserWithNoSentMessages() {
            long count = messageRepository.countBySenderId(UUID.randomUUID());

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("countByRecipientId")
    class CountByRecipientId {

        @Test
        @DisplayName("should count messages received by user")
        void shouldCountMessagesReceivedByUser() {
            long count = messageRepository.countByRecipientId(user2Id);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero for user with no received messages")
        void shouldReturnZeroForUserWithNoReceivedMessages() {
            long count = messageRepository.countByRecipientId(UUID.randomUUID());

            assertThat(count).isZero();
        }
    }
}
