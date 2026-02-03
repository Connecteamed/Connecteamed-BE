package com.connecteamed.server.domain.collaboration.controller;

import java.io.IOException;
import java.util.ArrayList;
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
import com.connecteamed.server.domain.document.entity.Document;
import com.connecteamed.server.domain.document.repository.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollabSocketController extends TextWebSocketHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentRepository documentRepository; 

    private static final Map<String, Set<WebSocketSession>> localRoomSessions = new ConcurrentHashMap<>();
    private static final String HISTORY_KEY_PREFIX = "doc:history:";

    // === 1. 소켓 연결 시 (세션 등록만! 데이터 전송 X) ===
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        String docId = path.substring(path.lastIndexOf('/') + 1);
        session.getAttributes().put("docId", docId);

        // 방 세션에 추가
        localRoomSessions.computeIfAbsent(docId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        
        log.info("Session connected: {}", session.getId());
        // ★ 삭제됨: 여기서 loadFromDbToRedis나 sendHistoryToUser를 호출하지 마세요.
        // 클라이언트가 보내는 "JOIN" 메시지에서 처리해야 순서가 꼬이지 않습니다.
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
    }

    // === 3. 퇴장 시 ===
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = (String) session.getAttributes().get("docId");
        Set<WebSocketSession> sessions = localRoomSessions.get(docId);

        if (sessions != null) {
            sessions.remove(session);
            
            // 마지막 사람이 나가면 DB 저장
            if (sessions.isEmpty()) {
                log.info("Last user left doc {}. Saving...", docId);
                saveRedisToDb(docId);
                localRoomSessions.remove(docId);
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

    // ========================================================
    //  Private Methods (중복 제거 및 로직 통합)
    // ========================================================

    /**
     * [통합 메서드] 입장 시 DB 데이터(1타) + Redis 변경분(2타) 전송
     */
    private void processJoin(WebSocketSession session, String docId) throws IOException {
        // 1. DB에서 저장된 최신 스냅샷(Base) 전송
        Document doc = documentRepository.findById(Long.parseLong(docId)).orElse(null);
        if (doc != null && doc.getContent() != null) {
            SocketMessage dbMsg = new SocketMessage();
            dbMsg.setType("INITIAL_LOAD"); 
            dbMsg.setDocId(docId);
            dbMsg.setPayload(doc.getContent());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(dbMsg)));
        }

        // 2. Redis에 쌓인 실시간 변경분(Delta) 전송
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
            // 1. Redis에 있는 새로운 변경사항들 가져오기
            List<Object> newUpdates = redisTemplate.opsForList().range(key, 0, -1);
            if (newUpdates == null || newUpdates.isEmpty()) return;

            try {
                Document doc = documentRepository.findById(Long.parseLong(docId)).orElseThrow();
                
                // 2. 기존 DB에 저장된 내용 가져오기
                List<String> existingHistory = new ArrayList<>();
                String dbContent = doc.getContent();
                
                if (dbContent != null && !dbContent.isEmpty()) {
                    try {
                        // 기존 내용이 JSON 배열인지 확인하고 파싱
                        if (dbContent.trim().startsWith("[")) {
                            existingHistory = objectMapper.readValue(dbContent, new TypeReference<List<String>>() {});
                        } else {
                            // 만약 예전 방식(일반 텍스트)으로 저장된 거라면... 
                            // Yjs 히스토리랑 섞이면 안 되므로 일단 무시하거나 마이그레이션이 필요하지만,
                            // 지금은 "새로운 히스토리 시작"으로 간주합니다.
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse existing DB content as history list. Starting fresh.");
                    }
                }

                // 3. 기존 역사 + 새로운 변경사항 합치기 (Append)
                for (Object update : newUpdates) {
                    existingHistory.add((String) update);
                }

                // 4. 합친 전체 역사를 다시 JSON으로 변환해서 저장
                String mergedHistory = objectMapper.writeValueAsString(existingHistory);
                
                doc.updateText(docId, mergedHistory); 
                documentRepository.save(doc);

                // 5. Redis 비우기
                redisTemplate.delete(key);
                log.info("Document {} saved. Total history size: {}", docId, existingHistory.size());

            } catch (Exception e) {
                log.error("DB Save failed", e);
            }
        }
    
}
