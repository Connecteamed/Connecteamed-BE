package com.connecteamed.server.domain.collaboration.controller;

import com.connecteamed.server.domain.collaboration.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollabSocketController extends TextWebSocketHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // 현재 서버 인스턴스에 연결된 세션만 관리 (메모리)
    // Map<DocId, Set<Session>>
    private static final Map<String, Set<WebSocketSession>> localRoomSessions = new ConcurrentHashMap<>();

    // 1. 소켓 연결 시
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // URI에서 docId 추출 (예: /ws/docs/123 -> 123)
        String path = session.getUri().getPath();
        String docId = path.substring(path.lastIndexOf('/') + 1);

        session.getAttributes().put("docId", docId);
        
        localRoomSessions.computeIfAbsent(docId, k -> Collections.synchronizedSet(new HashSet<>()))
                         .add(session);

        log.info("Client connected: session={} doc={}", session.getId(), docId);
    }

    // 2. 메시지 수신 (클라이언트 -> 서버)
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SocketMessage msg = objectMapper.readValue(message.getPayload(), SocketMessage.class);
        
        // 보낸 사람 ID 주입 (나중에 내가 보낸 건지 식별하기 위함)
        msg.setUserId(session.getId());

        // Redis로 전송 (Scale-out 대응: 모든 서버가 이 메시지를 받음)
        redisTemplate.convertAndSend("doc-channel", msg);
    }

    // 3. 연결 종료 시
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = (String) session.getAttributes().get("docId");
        Set<WebSocketSession> sessions = localRoomSessions.get(docId);
        
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                localRoomSessions.remove(docId);
            }
        }
        log.info("Client disconnected: session={}", session.getId());
    }

    // 4. Redis 구독자로부터 호출되는 메서드 (서버 -> 클라이언트 브로드캐스트)
    public void broadcastToLocal(SocketMessage msg) {
        Set<WebSocketSession> sessions = localRoomSessions.get(msg.getDocId());
        
        if (sessions != null) {
            sessions.forEach(session -> {
                // 보낸 당사자가 아니면 메시지 전송 (Echo 방지)
                // (만약 클라이언트가 Echo를 원하면 이 조건문 제거)
                if (session.isOpen() && !session.getId().equals(msg.getUserId())) {
                    try {
                        String json = objectMapper.writeValueAsString(msg);
                        session.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        log.error("Broadcast failed", e);
                    }
                }
            });
        }
    }
}
