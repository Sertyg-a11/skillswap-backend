package nl.ak.skillswap.messageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Manages WebSocket session tracking in Redis for horizontal scaling.
 * Allows multiple service instances to know which users are connected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketSessionService {

    private static final String SESSION_PREFIX = "ws:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * Register a user's WebSocket session.
     * Called when user connects via WebSocket.
     */
    public void registerSession(UUID userId, String sessionId) {
        String key = SESSION_PREFIX + userId;
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, SESSION_TTL);
        log.debug("Registered WebSocket session {} for user {}", sessionId, userId);
    }

    /**
     * Remove a user's WebSocket session.
     * Called when user disconnects.
     */
    public void removeSession(UUID userId, String sessionId) {
        String key = SESSION_PREFIX + userId;
        redisTemplate.opsForSet().remove(key, sessionId);
        log.debug("Removed WebSocket session {} for user {}", sessionId, userId);
    }

    /**
     * Check if a user has any active WebSocket sessions.
     */
    public boolean isUserOnline(UUID userId) {
        String key = SESSION_PREFIX + userId;
        Long size = redisTemplate.opsForSet().size(key);
        return size != null && size > 0;
    }

    /**
     * Get all active session IDs for a user.
     */
    public Set<String> getUserSessions(UUID userId) {
        String key = SESSION_PREFIX + userId;
        Set<String> sessions = redisTemplate.opsForSet().members(key);
        return sessions != null ? sessions : Set.of();
    }

    /**
     * Refresh session TTL (call periodically to keep session alive).
     */
    public void refreshSession(UUID userId) {
        String key = SESSION_PREFIX + userId;
        redisTemplate.expire(key, SESSION_TTL);
    }
}
