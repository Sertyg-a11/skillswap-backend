package nl.ak.skillswap.skillswap.massages.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private UUID userId;

    @Column(nullable=false, length=2000)
    private String content;

    @Column(name="created_at", nullable=false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    protected Message() {} // JPA

    public Message(UUID userId, String content, OffsetDateTime createdAt) {
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    // getters (and setters if you use them)
    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getContent() { return content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
