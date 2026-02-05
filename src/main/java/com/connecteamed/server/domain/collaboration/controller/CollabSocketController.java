package com.connecteamed.server.domain.collaboration.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.connecteamed.server.domain.collaboration.dto.SocketMessage;
import com.connecteamed.server.domain.collaboration.dto.UserPresenceDto;
import com.connecteamed.server.domain.collaboration.service.DocumentCollaborationService;
import com.connecteamed.server.domain.collaboration.service.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollabSocketController extends TextWebSocketHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final  PresenceService presenceService;

    private final DocumentCollaborationService collabService;

    private static final Map<String, Set<WebSocketSession>> localRoomSessions = new ConcurrentHashMap<>();
    private static final String HISTORY_KEY_PREFIX = "doc:history:";

    // === 1. 소켓 연결 시 (세션 등록만! 데이터 전송 X) ===
// === 1. 소켓 연결 시 ===
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        String docId = path.substring(path.lastIndexOf('/') + 1);
        session.getAttributes().put("docId", docId);
        
        // ★ [추가] Interceptor에서 넣어둔 유저 정보 가져오기
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");

        if (user != null) {
            // Redis 출석부에 등록 (문서별 접속자 관리)
            presenceService.addUser("doc", docId, user);
            log.info("User {} entered doc {}", user.getUserId(), docId);
        }

        // 방 세션에 추가
        localRoomSessions.computeIfAbsent(docId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        
        // ★ [추가] 입장했으니 최신 접속자 목록을 모두에게 알림
        broadcastUserList(docId);
    }

    // === 2. 메시지 처리 ===
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SocketMessage msg = objectMapper.readValue(message.getPayload(), SocketMessage.class);
        msg.setUserId(session.getId());
        String docId = (String) session.getAttributes().get("docId");

        // [핵심] JOIN 메시지가 오면 그때 DB+Redis 데이터를 순서대로 줍니다.
        if ("JOIN".equals(msg.getType())) {
            processJoin(session, docId);
            return; 
        }

        if ("UPDATE".equals(msg.getType())) {
            saveUpdateToRedis(docId, msg.getPayload());
            redisTemplate.convertAndSend("doc-channel", msg);
        }

        if ("SAVE_SNAPSHOT".equals(msg.getType())) {
            // Service에게 위임
            collabService.savePlainTextSnapshot(docId, msg.getPayload());
        }
    }

// === 3. 퇴장 시 ===
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = (String) session.getAttributes().get("docId");
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");
        Set<WebSocketSession> sessions = localRoomSessions.get(docId);

        if (sessions != null) {
            sessions.remove(session);
            
            // ★ [추가] Redis 출석부에서 제거
            if (user != null) {
                presenceService.removeUser("doc", docId, user);
            }

            // 마지막 사람이 나가면 DB 저장
            if (sessions.isEmpty()) {
                log.info("Last user left doc {}. Saving...", docId);
                saveRedisToDb(docId);
                localRoomSessions.remove(docId);
            } else {
                // 아직 사람이 남아있다면, 갱신된 접속자 목록 전송
                broadcastUserList(docId);
            }
        }
    }

    // === 4. Redis Pub/Sub 브로드캐스트 ===
    public void broadcastToLocal(SocketMessage msg) {
        Set<WebSocketSession> sessions = localRoomSessions.get(msg.getDocId());
        if (sessions != null) {
            sessions.forEach(session -> {
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
     * [통합 메서드] 입장 시 DB 데이터(1타) + Redis 변경분(2타) 전송
     */
    private void processJoin(WebSocketSession session, String docId) throws IOException {
        // 1. Service 호출 (트랜잭션 처리됨)
        String dbContent = collabService.getDocumentContent(docId);
        
        if (dbContent != null) {
            SocketMessage dbMsg = new SocketMessage();
            dbMsg.setType("INITIAL_LOAD");
            dbMsg.setDocId(docId);
            dbMsg.setPayload(dbContent);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(dbMsg)));
        }

        // 2. Redis 조회 (Redis는 트랜잭션 필요 없음)
        String key = HISTORY_KEY_PREFIX + docId;
        List<Object> redisHistory = redisTemplate.opsForList().range(key, 0, -1);
        
        if (redisHistory != null && !redisHistory.isEmpty()) {
            log.info("Sending {} redis updates to user {}", redisHistory.size(), session.getId());
            for (Object payload : redisHistory) {
                SocketMessage redisMsg = new SocketMessage();
                redisMsg.setType("UPDATE");
                redisMsg.setDocId(docId);
                redisMsg.setPayload((String) payload);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(redisMsg)));
            }
        }
    }

    private void saveUpdateToRedis(String docId, String payload) {
        String key = HISTORY_KEY_PREFIX + docId;
        redisTemplate.opsForList().rightPush(key, payload);
        redisTemplate.expire(key, 24, TimeUnit.HOURS); 
    }

    private void saveRedisToDb(String docId) {
        String key = HISTORY_KEY_PREFIX + docId;
        List<Object> newUpdates = redisTemplate.opsForList().range(key, 0, -1);
        
        if (newUpdates != null && !newUpdates.isEmpty()) {
            // 1. Service에게 저장 위임 (트랜잭션 안전)
            collabService.saveAndFlushHistory(docId, newUpdates);
            
            // 2. 저장이 성공했으면 Redis 비우기
            redisTemplate.delete(key);
        }
    }

    // 접속자 명단 전송 메서드
    private void broadcastUserList(String docId) {
        Set<Object> users = presenceService.getUsers("doc", docId);
        
        SocketMessage msg = new SocketMessage();
        msg.setType("PRESENCE_UPDATE"); // 클라이언트가 처리할 타입
        msg.setDocId(docId);
        try {
            msg.setPayload(objectMapper.writeValueAsString(users)); // 유저 목록 JSON
            
            // Redis Pub/Sub으로 전송 (그래야 다른 서버에 붙은 유저도 알 수 있음)
            redisTemplate.convertAndSend("doc-channel", msg);
        } catch (Exception e) {
            log.error("Presence broadcast failed", e);
        }
    }

}
