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
    private static final String PREVIEW_KEY_PREFIX = "doc:preview:";

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

        // if ("SAVE_SNAPSHOT".equals(msg.getType())) {
        //     // Service에게 위임
        //     collabService.savePlainTextSnapshot(docId, msg.getPayload());
        // }

        if ("SAVE_SNAPSHOT".equals(msg.getType())) {
            String key = PREVIEW_KEY_PREFIX + docId;
            redisTemplate.opsForValue().set(key, msg.getPayload(), 24, TimeUnit.HOURS);
            log.debug("Cached plain text snapshot to Redis for doc {}", docId);
        }
    }

    // === 3. 퇴장 시 ===
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = (String) session.getAttributes().get("docId");
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");
        
        // 1. 로컬 세션 정리
        Set<WebSocketSession> sessions = localRoomSessions.get(docId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                localRoomSessions.remove(docId);
            }
        }

        // 2. Redis 출석부에서 유저 제거
        if (user != null) {
            presenceService.removeUser("doc", docId, user);
        }

        // 3. [핵심 로직 변경]
        // "내 서버"의 세션이 비었는지가 아니라, "Redis(전체 서버)"에 아무도 없는지 확인
        Set<Object> remainingUsers = presenceService.getUsers("doc", docId);

        if (remainingUsers == null || remainingUsers.isEmpty()) {
            log.info("Users count is 0 for doc {}. Saving Yjs History to DB...", docId);
            
            // ★ 여기서 저장하는 건 "Yjs 히스토리(content)" 입니다.
            // plain_text는 위에서 SAVE_SNAPSHOT 메시지로 이미 저장되었을 겁니다.
            saveRedisToDb(docId); 
            
            // (선택) Presence 키 삭제 (깔끔하게)
            // redisTemplate.delete("presence:doc:" + docId);
        } else {
            // 아직 누군가 남아있으면 접속자 목록 갱신 방송
            broadcastUserList(docId);
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

    // === 4. DB 저장 메서드 수정 ===
    private void saveRedisToDb(String docId) {
        String historyKey = HISTORY_KEY_PREFIX + docId;
        String previewKey = PREVIEW_KEY_PREFIX + docId; // ★ 추가

        // 1. Redis에서 변경분 가져오기
        List<Object> newUpdates = redisTemplate.opsForList().range(historyKey, 0, -1);
        
        // ★ [추가] Redis에서 최신 스냅샷(Plain Text) 가져오기
        String latestSnapshot = (String) redisTemplate.opsForValue().get(previewKey);

        // 변경사항이나 스냅샷이 있을 때만 저장 시도
        if ((newUpdates != null && !newUpdates.isEmpty()) || latestSnapshot != null) {
            
            // Service에게 저장 위임 (히스토리 + 스냅샷 같이 넘김)
            // 메서드 시그니처를 바꿔야 합니다 (아래 서비스 코드 참고)
            collabService.saveAndFlushHistory(docId, newUpdates, latestSnapshot);
            
            // Redis 청소
            redisTemplate.delete(historyKey);
            redisTemplate.delete(previewKey); // ★ 스냅샷 키도 삭제
            
            log.info("Saved DB (History & Snapshot) for doc {}", docId);
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
