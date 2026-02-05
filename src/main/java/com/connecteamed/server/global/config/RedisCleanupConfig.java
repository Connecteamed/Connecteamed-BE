package com.connecteamed.server.global.config;

import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisCleanupConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    // 서버가 시작되자마자 딱 1번 실행됩니다.
    @Bean
    public CommandLineRunner cleanupRedis() {
        return args -> {
            log.info("🧹 Server Starting... Cleaning up stale Redis data...");

            deleteKeysByPattern("doc:history:*");
            deleteKeysByPattern("doc:preview:*");
            deleteKeysByPattern("presence:*");
            
            log.info("✨ Redis cleanup complete.");
        };
    }

    private void deleteKeysByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Deleted {} keys matching '{}'", keys.size(), pattern);
        }
    }
}
