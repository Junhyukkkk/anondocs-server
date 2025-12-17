package com.anondocs.anondocs_server.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class RedisConfig{

@Bean
public LettuceConnectionFactory redisConnectionFactory(){
    return new LettuceConnectionFactory();
}

@Bean
public CacheManager cacheManager(RedisConnectionFactory factory){
    RedisCacheConfiguration config= RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );

    return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
}
}

