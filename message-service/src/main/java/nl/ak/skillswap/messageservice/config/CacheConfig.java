package nl.ak.skillswap.messageservice.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for optimizing performance.
 * Different TTLs for different cache types based on data volatility.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // User ID mapping (externalId -> databaseId) - cache for 24h as this rarely changes
        cacheConfigs.put("user-id-mapping", defaultConfig.entryTtl(Duration.ofHours(24)));

        // User existence check - cache for longer as users rarely get deleted
        cacheConfigs.put("user-exists", defaultConfig.entryTtl(Duration.ofHours(1)));

        // User messaging permissions - shorter TTL as blocking can change
        cacheConfigs.put("user-can-message", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Conversation metadata - moderate TTL
        cacheConfigs.put("conversations", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Unread counts - short TTL as these change frequently
        cacheConfigs.put("unread-counts", defaultConfig.entryTtl(Duration.ofMinutes(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
