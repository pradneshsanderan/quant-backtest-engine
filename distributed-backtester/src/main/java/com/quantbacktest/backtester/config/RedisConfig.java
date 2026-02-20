package com.quantbacktest.backtester.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration for caching and distributed data storage.
 */
@Configuration
@EnableCaching
public class RedisConfig {

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);

                // Use String serializer for keys
                template.setKeySerializer(new StringRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());

                // Use JSON serializer for values
                template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
                template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

                template.afterPropertiesSet();
                return template;
        }

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                // Default cache configuration (1 hour TTL)
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                .disableCachingNullValues();

                // Market data cache configuration (10 minutes TTL)
                RedisCacheConfiguration marketDataConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                .disableCachingNullValues();

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withCacheConfiguration("marketData", marketDataConfig)
                                .transactionAware()
                                .build();
        }
}
