package nl.ak.skillswap.messageservice.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Conversation")
class ConversationTest {

    private UUID userId1;
    private UUID userId2;
    private UUID outsiderId;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        userId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        outsiderId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        conversation = Conversation.builder()
                .id(UUID.randomUUID())
                .userLowId(userId1)
                .userHighId(userId2)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("involves")
    class Involves {

        @Test
        @DisplayName("should return true for userLowId")
        void shouldReturnTrueForUserLowId() {
            assertThat(conversation.involves(userId1)).isTrue();
        }

        @Test
        @DisplayName("should return true for userHighId")
        void shouldReturnTrueForUserHighId() {
            assertThat(conversation.involves(userId2)).isTrue();
        }

        @Test
        @DisplayName("should return false for outsider")
        void shouldReturnFalseForOutsider() {
            assertThat(conversation.involves(outsiderId)).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(conversation.involves(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("otherParticipant")
    class OtherParticipant {

        @Test
        @DisplayName("should return userHighId when called with userLowId")
        void shouldReturnUserHighIdWhenCalledWithUserLowId() {
            UUID other = conversation.otherParticipant(userId1);
            assertThat(other).isEqualTo(userId2);
        }

        @Test
        @DisplayName("should return userLowId when called with userHighId")
        void shouldReturnUserLowIdWhenCalledWithUserHighId() {
            UUID other = conversation.otherParticipant(userId2);
            assertThat(other).isEqualTo(userId1);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for outsider")
        void shouldThrowForOutsider() {
            assertThatThrownBy(() -> conversation.otherParticipant(outsiderId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User is not part of the conversation");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> conversation.otherParticipant(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("prePersist")
    class PrePersist {

        @Test
        @DisplayName("should generate UUID if null")
        void shouldGenerateUuidIfNull() {
            Conversation conv = new Conversation();
            conv.prePersist();
            assertThat(conv.getId()).isNotNull();
        }

        @Test
        @DisplayName("should set createdAt if null")
        void shouldSetCreatedAtIfNull() {
            Conversation conv = new Conversation();
            conv.prePersist();
            assertThat(conv.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should not override existing UUID")
        void shouldNotOverrideExistingUuid() {
            UUID existingId = UUID.randomUUID();
            Conversation conv = Conversation.builder().id(existingId).build();
            conv.prePersist();
            assertThat(conv.getId()).isEqualTo(existingId);
        }

        @Test
        @DisplayName("should not override existing createdAt")
        void shouldNotOverrideExistingCreatedAt() {
            Instant existingTime = Instant.parse("2024-01-01T00:00:00Z");
            Conversation conv = Conversation.builder().createdAt(existingTime).build();
            conv.prePersist();
            assertThat(conv.getCreatedAt()).isEqualTo(existingTime);
        }
    }
}
