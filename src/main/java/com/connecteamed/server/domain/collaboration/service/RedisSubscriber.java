package com.connecteamed.server.domain.collaboration.service;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.connecteamed.server.domain.collaboration.controller.CollabSocketController;
import com.connecteamed.server.domain.collaboration.controller.ProjectPresenceController;
import com.connecteamed.server.domain.collaboration.dto.SocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private final CollabSocketController collabController;
    private final ProjectPresenceController projectPresenceController; // ★ 주입 추가

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String publishMessage = (String) redisTemplate.getStringSerializer().deserialize(message.getBody());
            SocketMessage msg = objectMapper.readValue(publishMessage, SocketMessage.class);

            String channel = new String(message.getChannel());

            if ("doc-channel".equals(channel)) {
                collabController.broadcastToLocal(msg);
            } 
            // ★ [추가] 프로젝트 채널 메시지 처리
            else if ("project-channel".equals(channel)) {
                projectPresenceController.broadcastToLocalSessions(msg);
            }

        } catch (Exception e) {
            log.error("Redis onMessage error: " + e.getMessage());
        }
    }
}
