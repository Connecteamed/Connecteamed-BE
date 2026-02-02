package com.connecteamed.server.domain.collaboration.service;

import org.springframework.stereotype.Service;

import com.connecteamed.server.domain.collaboration.controller.CollabSocketController;
import com.connecteamed.server.domain.collaboration.dto.SocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber {

    private final ObjectMapper objectMapper;
    private final CollabSocketController socketController;

    /**
     * Redis 메시지 리스너 (RedisConfig에서 등록됨)
     */
    public void onMessage(String messageJson) {
        try {
            SocketMessage msg = objectMapper.readValue(messageJson, SocketMessage.class);
            // 핸들러를 통해 현재 서버에 붙은 클라이언트들에게 전송
            socketController.broadcastToLocal(msg);
        } catch (Exception e) {
            log.error("Redis message receive error", e);
        }
    }
}
