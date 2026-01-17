package nl.ak.skillswap.messageservice.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.support.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Distributed rate limiting service using Bucket4j with Redis backend.
 * OWASP: Prevents API abuse, brute force attacks, and DoS attempts.
 */
@Slf4j
@Service
public class RateLimitingService {

    @Value("${app.rate-limiting.messages-per-minute:30}")
    private int messagesPerMinute;

    @Value("${app.rate-limiting.messages-burst:10}")
    private int messagesBurst;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private ProxyManager<String> proxyManager;

    @PostConstruct
    public void init() {
        RedisClient redisClient = RedisClient.create("redis://" + redisHost + ":" + redisPort);
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        );

        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .build();

        log.info("Rate limiting initialized: {} messages/min, burst: {}", messagesPerMinute, messagesBurst);
    }

    /**
     * Check if user can send a message (rate limiting).
     *
     * @param userId the user ID to check
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkMessageRateLimit(UUID userId) {
        String key = "rate:msg:" + userId;
        Bucket bucket = proxyManager.builder()
                .build(key, getMessageRateLimitConfig());

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for user: {}", userId);
            throw new RateLimitExceededException("Too many messages. Please wait before sending more.");
        }
    }

    /**
     * Check WebSocket connection rate limit.
     *
     * @param userId the user ID to check
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkConnectionRateLimit(UUID userId) {
        String key = "rate:ws:" + userId;
        Bucket bucket = proxyManager.builder()
                .build(key, getConnectionRateLimitConfig());

        if (!bucket.tryConsume(1)) {
            log.warn("WebSocket connection rate limit exceeded for user: {}", userId);
            throw new RateLimitExceededException("Too many connection attempts. Please wait.");
        }
    }

    /**
     * Check typing indicator rate limit (prevent spam).
     */
    public void checkTypingRateLimit(UUID userId) {
        String key = "rate:typing:" + userId;
        Bucket bucket = proxyManager.builder()
                .build(key, getTypingRateLimitConfig());

        if (!bucket.tryConsume(1)) {
            // Silently ignore typing spam - no exception thrown
            log.debug("Typing rate limit exceeded for user: {}", userId);
        }
    }

    private Supplier<BucketConfiguration> getMessageRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(messagesBurst)
                        .refillGreedy(messagesPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Supplier<BucketConfiguration> getConnectionRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)  // Max 5 connections burst
                        .refillGreedy(10, Duration.ofMinutes(1))  // 10 connections/minute
                        .build())
                .build();
    }

    private Supplier<BucketConfiguration> getTypingRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)  // Max 3 typing events burst
                        .refillGreedy(10, Duration.ofSeconds(10))  // 10 per 10 seconds
                        .build())
                .build();
    }
}
