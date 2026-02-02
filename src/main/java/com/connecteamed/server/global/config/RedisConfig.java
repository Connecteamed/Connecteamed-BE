package com.connecteamed.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.connecteamed.server.domain.collaboration.service.RedisSubscriber;

@Configuration
public class RedisConfig {

    /**
     * Redis Pub/Sub 메시지 브로커 컨테이너
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // "doc-channel"이라는 토픽으로 오가는 모든 메시지를 청취
        container.addMessageListener(listenerAdapter, new PatternTopic("doc-channel"));
        return container;
    }

    /**
     * 실제 메시지를 처리하는 리스너 어댑터
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        // RedisSubscriber 클래스의 "onMessage" 메서드를 실행하도록 지정
        return new MessageListenerAdapter(subscriber, "onMessage");
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
