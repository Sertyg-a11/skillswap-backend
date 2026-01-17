package nl.ak.skillswap.messageservice.service;

import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.support.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService")
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ConversationService conversationService;

    private UUID userId1;
    private UUID userId2;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        conversationId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("getOrCreate")
    class GetOrCreate {

        @Test
        @DisplayName("should return existing conversation when found")
        void shouldReturnExistingConversation() {
            Conversation existing = Conversation.builder()
                    .id(conversationId)
                    .userLowId(userId1)
                    .userHighId(userId2)
                    .createdAt(Instant.now())
                    .build();

            when(conversationRepository.findByUserLowIdAndUserHighId(userId1, userId2))
                    .thenReturn(Optional.of(existing));

            Conversation result = conversationService.getOrCreate(userId1, userId2);

            assertThat(result).isEqualTo(existing);
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create new conversation when not found")
        void shouldCreateNewConversationWhenNotFound() {
            when(conversationRepository.findByUserLowIdAndUserHighId(userId1, userId2))
                    .thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
                Conversation c = invocation.getArgument(0);
                c.setId(conversationId);
                return c;
            });

            Conversation result = conversationService.getOrCreate(userId1, userId2);

            assertThat(result.getUserLowId()).isEqualTo(userId1);
            assertThat(result.getUserHighId()).isEqualTo(userId2);
            assertThat(result.getLastMessageAt()).isNull();

            verify(conversationRepository).save(any(Conversation.class));
        }

        @Test
        @DisplayName("should normalize user IDs correctly when first user is lower")
        void shouldNormalizeUserIdsWhenFirstIsLower() {
            when(conversationRepository.findByUserLowIdAndUserHighId(userId1, userId2))
                    .thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Call with userId1 first (which is lower)
            conversationService.getOrCreate(userId1, userId2);

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            assertThat(captor.getValue().getUserLowId()).isEqualTo(userId1);
            assertThat(captor.getValue().getUserHighId()).isEqualTo(userId2);
        }

        @Test
        @DisplayName("should normalize user IDs correctly when second user is lower")
        void shouldNormalizeUserIdsWhenSecondIsLower() {
            when(conversationRepository.findByUserLowIdAndUserHighId(userId1, userId2))
                    .thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Call with userId2 first (but userId1 is lower)
            conversationService.getOrCreate(userId2, userId1);

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            // Should still have userId1 as low and userId2 as high
            assertThat(captor.getValue().getUserLowId()).isEqualTo(userId1);
            assertThat(captor.getValue().getUserHighId()).isEqualTo(userId2);
        }

        @Test
        @DisplayName("should return same conversation regardless of parameter order")
        void shouldReturnSameConversationRegardlessOfOrder() {
            Conversation existing = Conversation.builder()
                    .id(conversationId)
                    .userLowId(userId1)
                    .userHighId(userId2)
                    .createdAt(Instant.now())
                    .build();

            when(conversationRepository.findByUserLowIdAndUserHighId(userId1, userId2))
                    .thenReturn(Optional.of(existing));

            // Call in both orders
            Conversation result1 = conversationService.getOrCreate(userId1, userId2);
            Conversation result2 = conversationService.getOrCreate(userId2, userId1);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("getOrThrow")
    class GetOrThrow {

        @Test
        @DisplayName("should return conversation when found")
        void shouldReturnConversationWhenFound() {
            Conversation existing = Conversation.builder()
                    .id(conversationId)
                    .userLowId(userId1)
                    .userHighId(userId2)
                    .createdAt(Instant.now())
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(existing));

            Conversation result = conversationService.getOrThrow(conversationId);

            assertThat(result).isEqualTo(existing);
        }

        @Test
        @DisplayName("should throw NotFoundException when not found")
        void shouldThrowNotFoundExceptionWhenNotFound() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> conversationService.getOrThrow(conversationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Conversation not found");
        }
    }

    @Nested
    @DisplayName("listForUser")
    class ListForUser {

        @Test
        @DisplayName("should return conversations for user")
        void shouldReturnConversationsForUser() {
            Conversation conv1 = Conversation.builder().id(UUID.randomUUID()).build();
            Conversation conv2 = Conversation.builder().id(UUID.randomUUID()).build();

            when(conversationRepository.findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(userId1, userId1))
                    .thenReturn(List.of(conv1, conv2));

            List<Conversation> result = conversationService.listForUser(userId1);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(conv1, conv2);
        }

        @Test
        @DisplayName("should return empty list when no conversations")
        void shouldReturnEmptyListWhenNoConversations() {
            when(conversationRepository.findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(userId1, userId1))
                    .thenReturn(List.of());

            List<Conversation> result = conversationService.listForUser(userId1);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("touchLastMessage")
    class TouchLastMessage {

        @Test
        @DisplayName("should update lastMessageAt and save")
        void shouldUpdateLastMessageAtAndSave() {
            Conversation conversation = Conversation.builder()
                    .id(conversationId)
                    .userLowId(userId1)
                    .userHighId(userId2)
                    .createdAt(Instant.now())
                    .lastMessageAt(null)
                    .build();

            Instant newTime = Instant.now();

            conversationService.touchLastMessage(conversation, newTime);

            assertThat(conversation.getLastMessageAt()).isEqualTo(newTime);
            verify(conversationRepository).save(conversation);
        }
    }
}
