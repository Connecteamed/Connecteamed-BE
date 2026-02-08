package com.connecteamed.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.connecteamed.server.domain.collaboration.service.RedisSubscriber;

@Configuration
public class RedisConfig {

    /**
     * Redis Pub/Sub 메시지 리스너 컨테이너
     * (기존 중복 메서드를 제거하고 하나로 통합했습니다)
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListener(
            RedisConnectionFactory connectionFactory,
            RedisSubscriber redisSubscriber // ★ 핵심: 여기에 주입받아야 에러가 안 납니다.
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 1. 문서 편집 채널 구독 (doc-channel)
        // RedisSubscriber가 MessageListener 인터페이스를 구현했으므로 바로 넣을 수 있습니다.
        container.addMessageListener(redisSubscriber, new PatternTopic("doc-channel"));

        // 2. 프로젝트 접속자 채널 구독 (project-channel)
        container.addMessageListener(redisSubscriber, new PatternTopic("project-channel"));

        return container;
    }

    /**
     * RedisTemplate (JSON 직렬화 설정)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return template;
    }
}
