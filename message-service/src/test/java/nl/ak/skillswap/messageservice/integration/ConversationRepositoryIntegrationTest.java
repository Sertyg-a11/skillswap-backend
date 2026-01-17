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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConversationRepository Integration Tests")
class ConversationRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private UUID user1Id;
    private UUID user2Id;
    private UUID user3Id;
    private Conversation conversation1;
    private Conversation conversation2;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();
        user3Id = UUID.randomUUID();

        // Ensure proper ordering for userLowId/userHighId
        UUID lowId1 = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
        UUID highId1 = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;

        UUID lowId2 = user1Id.compareTo(user3Id) < 0 ? user1Id : user3Id;
        UUID highId2 = user1Id.compareTo(user3Id) < 0 ? user3Id : user1Id;

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
    }

    @Nested
    @DisplayName("findByUserLowIdAndUserHighId")
    class FindByUserLowIdAndUserHighId {

        @Test
        @DisplayName("should find conversation by user pair")
        void shouldFindConversationByUserPair() {
            UUID lowId = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
            UUID highId = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;

            Optional<Conversation> result = conversationRepository.findByUserLowIdAndUserHighId(lowId, highId);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(conversation1.getId());
        }

        @Test
        @DisplayName("should return empty when no conversation exists")
        void shouldReturnEmptyWhenNotFound() {
            UUID randomUser = UUID.randomUUID();
            UUID lowId = user1Id.compareTo(randomUser) < 0 ? user1Id : randomUser;
            UUID highId = user1Id.compareTo(randomUser) < 0 ? randomUser : user1Id;

            Optional<Conversation> result = conversationRepository.findByUserLowIdAndUserHighId(lowId, highId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc")
    class FindByUserIdOrdered {

        @Test
        @DisplayName("should find all conversations for user ordered by lastMessageAt")
        void shouldFindAllConversationsForUserOrdered() {
            List<Conversation> results = conversationRepository
                    .findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(user1Id, user1Id);

            assertThat(results).hasSize(2);
            // Conversation1 has more recent lastMessageAt
            assertThat(results.get(0).getId()).isEqualTo(conversation1.getId());
            assertThat(results.get(1).getId()).isEqualTo(conversation2.getId());
        }

        @Test
        @DisplayName("should return empty list for user with no conversations")
        void shouldReturnEmptyForUserWithNoConversations() {
            UUID newUser = UUID.randomUUID();

            List<Conversation> results = conversationRepository
                    .findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(newUser, newUser);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByUserId")
    class FindAllByUserId {

        @Test
        @DisplayName("should find all conversations involving user")
        void shouldFindAllConversationsInvolvingUser() {
            List<Conversation> results = conversationRepository.findAllByUserId(user1Id);

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("should find conversation as either participant")
        void shouldFindConversationAsEitherParticipant() {
            List<Conversation> results1 = conversationRepository.findAllByUserId(user2Id);
            List<Conversation> results2 = conversationRepository.findAllByUserId(user3Id);

            assertThat(results1).hasSize(1);
            assertThat(results2).hasSize(1);
        }

        @Test
        @DisplayName("should return ordered by lastMessageAt")
        void shouldReturnOrderedByLastMessageAt() {
            List<Conversation> results = conversationRepository.findAllByUserId(user1Id);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getLastMessageAt())
                    .isAfterOrEqualTo(results.get(1).getLastMessageAt());
        }
    }

    @Nested
    @DisplayName("countByUserId")
    class CountByUserId {

        @Test
        @DisplayName("should count conversations for user")
        void shouldCountConversationsForUser() {
            long count = conversationRepository.countByUserId(user1Id);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero for user with no conversations")
        void shouldReturnZeroForUserWithNoConversations() {
            long count = conversationRepository.countByUserId(UUID.randomUUID());

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("deleteEmptyConversationsByUserId")
    @Transactional
    class DeleteEmptyConversationsByUserId {

        @Test
        @DisplayName("should delete empty conversations for user")
        void shouldDeleteEmptyConversationsForUser() {
            // conversation1 and conversation2 have no messages, so they are "empty"
            int deleted = conversationRepository.deleteEmptyConversationsByUserId(user1Id);

            assertThat(deleted).isEqualTo(2);
            assertThat(conversationRepository.findAllByUserId(user1Id)).isEmpty();
        }

        @Test
        @DisplayName("should not delete conversations with messages")
        void shouldNotDeleteConversationsWithMessages() {
            // Add a message to conversation1
            messageRepository.save(Message.builder()
                    .id(UUID.randomUUID())
                    .conversationId(conversation1.getId())
                    .senderId(user1Id)
                    .recipientId(user2Id)
                    .body("Test message")
                    .createdAt(Instant.now())
                    .build());

            int deleted = conversationRepository.deleteEmptyConversationsByUserId(user1Id);

            // Only conversation2 should be deleted (empty)
            assertThat(deleted).isEqualTo(1);
            assertThat(conversationRepository.findById(conversation1.getId())).isPresent();
            assertThat(conversationRepository.findById(conversation2.getId())).isEmpty();
        }
    }
}
