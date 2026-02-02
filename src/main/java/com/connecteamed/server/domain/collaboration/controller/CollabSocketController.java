package com.connecteamed.server.domain.collaboration.controller;

import com.connecteamed.server.domain.collaboration.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollabSocketController extends TextWebSocketHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // 현재 서버 인스턴스에 연결된 세션만 관리 (메모리)
    // Map<DocId, Set<Session>>
    private static final Map<String, Set<WebSocketSession>> localRoomSessions = new ConcurrentHashMap<>();
    private static final String HISTORY_KEY_PREFIX = "doc:history:";

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
            msg.setUserId(session.getId());
            String docId = (String) session.getAttributes().get("docId");

            // 1. 입장(JOIN) 메시지인 경우 -> 지금까지의 히스토리를 얘한테만 다 쏴줌
            if ("JOIN".equals(msg.getType())) {
                sendHistoryToUser(session, docId);
                return; // JOIN 메시지는 브로드캐스트 하지 않음 (필요 시 변경 가능)
            }

            // 2. 업데이트(UPDATE) 메시지인 경우 -> Redis에 저장 후 브로드캐스트
            if ("UPDATE".equals(msg.getType())) {
                saveUpdateToRedis(docId, msg.getPayload());
            }

            // 3. 다른 서버들에게 전파 (기존 로직)
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

    /**
     * [핵심] Redis에 저장된 업데이트 내역을 싹 긁어서 신규 유저에게 전송
     */
    private void sendHistoryToUser(WebSocketSession session, String docId) {
        String key = HISTORY_KEY_PREFIX + docId;
        // Redis List에서 0번부터 끝(-1)까지 다 가져옴
        ListOperations<String, Object> listOps = redisTemplate.opsForList();
        List<Object> history = listOps.range(key, 0, -1);

        if (history != null) {
            log.info("Sending history to user {}: {} items", session.getId(), history.size());
            for (Object updatePayload : history) {
                try {
                    // 과거 기록을 UPDATE 타입으로 포장해서 전송
                    SocketMessage historyMsg = new SocketMessage();
                    historyMsg.setType("UPDATE");
                    historyMsg.setDocId(docId);
                    historyMsg.setPayload((String) updatePayload); // Base64 String
                    
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(historyMsg)));
                } catch (IOException e) {
                    log.error("Error sending history", e);
                }
            }
        }
    }

    /**
     * [핵심] 업데이트 내역을 Redis List에 저장 (Append)
     */
    private void saveUpdateToRedis(String docId, String payload) {
        String key = HISTORY_KEY_PREFIX + docId;
        redisTemplate.opsForList().rightPush(key, payload);
        
        // (선택사항) 너무 많이 쌓이면 메모리 터지니까 TTL 설정 (예: 1일)
        redisTemplate.expire(key, 24, TimeUnit.HOURS); 
    }
}
