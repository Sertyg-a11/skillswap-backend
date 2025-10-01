package nl.ak.skillswap.skillswap.massages.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Message {
    private final long id;
    private final UUID userId;
    private final String content;
    private final OffsetDateTime createdAt;

    public Message(long id, UUID userId, String content, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getContent() { return content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
