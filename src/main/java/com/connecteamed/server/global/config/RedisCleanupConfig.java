package com.connecteamed.server.global.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

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

    /**
     * SCAN 명령어를 사용하여 Keys를 조회하고 삭제 (Non-blocking 방식)
     */
    private void deleteKeysByPattern(String pattern) {
        // 1. SCAN 옵션 설정 (패턴 매칭, 한 번에 100개씩 조회)
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        // 2. Cursor를 통해 순회 (Try-with-resources로 자동 닫기 필수)
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> keysToDelete = new ArrayList<>();
            
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());

                // 3. 성능을 위해 100개씩 모아서 일괄 삭제
                if (keysToDelete.size() >= 100) {
                    redisTemplate.delete(keysToDelete);
                    keysToDelete.clear();
                }
            }
            
            // 4. 남은 키 삭제
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            log.error("Failed to scan/delete keys for pattern: {}", pattern, e);
        }
    }
}
