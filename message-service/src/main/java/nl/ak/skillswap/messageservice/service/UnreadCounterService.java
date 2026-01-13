package nl.ak.skillswap.messageservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnreadCounterService {

    private final StringRedisTemplate redis;

    private String key(UUID userId) {
        return "unread:" + userId;
    }

    public void incrementUnread(UUID recipientId, UUID conversationId) {
        redis.opsForHash().increment(key(recipientId), conversationId.toString(), 1);
    }

    public void clearUnread(UUID recipientId, UUID conversationId) {
        redis.opsForHash().delete(key(recipientId), conversationId.toString());
    }

    public long getUnread(UUID recipientId, UUID conversationId) {
        Object v = redis.opsForHash().get(key(recipientId), conversationId.toString());
        if (v == null) return 0;
        try {
            return Long.parseLong(v.toString());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public Map<Object, Object> getAllUnread(UUID recipientId) {
        return redis.opsForHash().entries(key(recipientId));
    }
}
