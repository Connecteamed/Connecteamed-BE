package com.connecteamed.server.domain.collaboration.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.connecteamed.server.domain.collaboration.dto.UserPresenceDto;
import com.connecteamed.server.domain.collaboration.service.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProjectPresenceController extends TextWebSocketHandler {

    private final PresenceService presenceService;
    private final ObjectMapper objectMapper;
    // 프로젝트별 세션 관리 (Map<ProjectId, Set<Session>>)
    private static final Map<String, Set<WebSocketSession>> projectSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath(); 
        // url: /ws/project/{projectId} 라고 가정
        String projectId = path.substring(path.lastIndexOf('/') + 1);
        session.getAttributes().put("projectId", projectId);
        
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");

        // 세션 등록
        projectSessions.computeIfAbsent(projectId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);

        // Redis 등록
        presenceService.addUser("project", projectId, user);

        // 접속자 명단 전송
        broadcast(projectId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String projectId = (String) session.getAttributes().get("projectId");
        UserPresenceDto user = (UserPresenceDto) session.getAttributes().get("user");

        // 세션/Redis 제거
        Set<WebSocketSession> sessions = projectSessions.get(projectId);
        if (sessions != null) sessions.remove(session);
        presenceService.removeUser("project", projectId, user);

        // 접속자 명단 전송
        broadcast(projectId);
    }

    private void broadcast(String projectId) throws IOException {
        Set<Object> users = presenceService.getUsers("project", projectId);
        String userListJson = objectMapper.writeValueAsString(users);
        
        Set<WebSocketSession> sessions = projectSessions.get(projectId);
        if (sessions != null) {
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(userListJson));
                }
            }
        }
    }
}
