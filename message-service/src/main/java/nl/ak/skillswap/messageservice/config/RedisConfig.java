package nl.ak.skillswap.messageservice.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RedisConfig {
    // You can add RedisCacheManager customization later if needed.
}