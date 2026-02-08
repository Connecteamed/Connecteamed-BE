package com.connecteamed.server.domain.collaboration.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.connecteamed.server.domain.collaboration.dto.SocketMessage;
import com.connecteamed.server.domain.collaboration.dto.UserPresenceDto;
import com.connecteamed.server.domain.collaboration.service.PresenceService;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectPresenceController extends TextWebSocketHandler {

    private final PresenceService presenceService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProjectMemberRepository projectMemberRepository; // ★ 권한 체크용

    // 로컬 세션 관리 (내 서버에 연결된 소켓들)
    private static final Map<String, Set<WebSocketSession>> localProjectSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath(); 
        String projectId = path.substring(path.lastIndexOf('/') + 1);
        session.getAttributes().put("projectId", projectId);
        
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");

        // 1. [보안] 프로젝트 멤버인지 확인 (IDOR 방지)
        boolean isMember = projectMemberRepository.existsByProjectIdAndMemberLoginId(
                Long.parseLong(projectId), 
                user.getUserId()
        );

        if (!isMember) {
            log.warn("Unauthorized access attempt to project {} by user {}", projectId, user.getUserId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 2. 세션 등록
        localProjectSessions.computeIfAbsent(projectId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);

        // 3. Redis 출석부 등록
        presenceService.addUser("project", projectId, user);

        // 4. [확장성] Redis로 "접속자 명단 갱신" 방송 (Broadcast to Redis)
        broadcastToRedis(projectId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String projectId = (String) session.getAttributes().get("projectId");
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");

        // 세션 제거
        Set<WebSocketSession> sessions = localProjectSessions.get(projectId);
        if (sessions != null) sessions.remove(session);
        
        // Redis 출석부 제거
        if (user != null) {
            presenceService.removeUser("project", projectId, user);
        }

        // 퇴장 시에도 명단 갱신 방송
        broadcastToRedis(projectId);
    }

    /**
     * [발신] Redis 채널(project-channel)에 메시지를 쏩니다.
     * 모든 서버 인스턴스의 RedisSubscriber가 이걸 받습니다.
     */
    private void broadcastToRedis(String projectId) {
        Set<Object> users = presenceService.getUsers("project", projectId);
        try {
            SocketMessage msg = new SocketMessage();
            msg.setType("PRESENCE_UPDATE");
            msg.setDocId(projectId); // 여기서는 docId 필드를 projectId로 재활용
            msg.setPayload(objectMapper.writeValueAsString(users));
            
            redisTemplate.convertAndSend("project-channel", msg); 
        } catch (Exception e) {
            log.error("Redis broadcast failed", e);
        }
    }

    /**
     * [수신] RedisSubscriber가 호출하는 메서드입니다.
     * 전달받은 메시지를 '내 서버'에 연결된 클라이언트들에게 뿌립니다.
     */
    public void broadcastToLocalSessions(SocketMessage msg) {
        String projectId = msg.getDocId(); // projectId로 사용
        Set<WebSocketSession> sessions = localProjectSessions.get(projectId);
        
        if (sessions != null) {
            try {
                TextMessage textMsg = new TextMessage(objectMapper.writeValueAsString(msg));
                synchronized (sessions) { // 동시성 이슈 방지
                    for (WebSocketSession s : sessions) {
                        if (s.isOpen()) {
                            s.sendMessage(textMsg);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Local broadcast failed", e);
            }
        }
    }
}
