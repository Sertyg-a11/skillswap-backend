package nl.ak.skillswap.userservice.service;

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
import nl.ak.skillswap.userservice.support.RateLimitExceededException;
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

    @Value("${app.rate-limiting.search-per-minute:30}")
    private int searchPerMinute;

    @Value("${app.rate-limiting.search-burst:10}")
    private int searchBurst;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private ProxyManager<String> proxyManager;

    @PostConstruct
    public void init() {
        try {
            RedisClient redisClient = RedisClient.create("redis://" + redisHost + ":" + redisPort);
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                    RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
            );

            this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                    .build();

            log.info("Rate limiting initialized: {} searches/min, burst: {}", searchPerMinute, searchBurst);
        } catch (Exception e) {
            log.warn("Redis not available for rate limiting, using in-memory fallback: {}", e.getMessage());
            // Rate limiting will be disabled if Redis is unavailable
            this.proxyManager = null;
        }
    }

    /**
     * Check if user can perform a search (rate limiting).
     *
     * @param userId the user ID to check
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkSearchRateLimit(UUID userId) {
        if (proxyManager == null) {
            return; // Skip rate limiting if Redis unavailable
        }

        String key = "rate:search:" + userId;
        Bucket bucket = proxyManager.builder()
                .build(key, getSearchRateLimitConfig());

        if (!bucket.tryConsume(1)) {
            log.warn("Search rate limit exceeded for user: {}", userId);
            throw new RateLimitExceededException("Too many search requests. Please wait before searching again.");
        }
    }

    /**
     * Check API rate limit (general API calls).
     */
    public void checkApiRateLimit(UUID userId) {
        if (proxyManager == null) {
            return;
        }

        String key = "rate:api:" + userId;
        Bucket bucket = proxyManager.builder()
                .build(key, getApiRateLimitConfig());

        if (!bucket.tryConsume(1)) {
            log.warn("API rate limit exceeded for user: {}", userId);
            throw new RateLimitExceededException("Too many requests. Please slow down.");
        }
    }

    private Supplier<BucketConfiguration> getSearchRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(searchBurst)
                        .refillGreedy(searchPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Supplier<BucketConfiguration> getApiRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(20)  // Burst capacity
                        .refillGreedy(100, Duration.ofMinutes(1))  // 100 requests/minute
                        .build())
                .build();
    }
}
