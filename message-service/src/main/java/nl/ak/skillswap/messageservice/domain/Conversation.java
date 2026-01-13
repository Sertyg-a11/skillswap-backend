package nl.ak.skillswap.messageservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private UUID id;

    @Column(name = "user_low_id", nullable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false)
    private UUID userHighId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean involves(UUID userId) {
        return userLowId.equals(userId) || userHighId.equals(userId);
    }

    public UUID otherParticipant(UUID me) {
        if (userLowId.equals(me)) return userHighId;
        if (userHighId.equals(me)) return userLowId;
        throw new IllegalArgumentException("User is not part of the conversation");
    }
}

