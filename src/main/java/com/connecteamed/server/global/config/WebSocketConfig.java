package com.connecteamed.server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.connecteamed.server.domain.collaboration.controller.CollabSocketController;
import com.connecteamed.server.domain.collaboration.controller.ProjectPresenceController;
import com.connecteamed.server.domain.collaboration.interceptor.JwtHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CollabSocketController collabSocketController;
    private final ProjectPresenceController projectPresenceController;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 엔드포인트: ws://localhost:8080/ws/docs/{docId}
        registry.addHandler(collabSocketController, "/ws/docs/{docId}")
                .addInterceptors(jwtHandshakeInterceptor) // ★ 토큰 검증
                .setAllowedOrigins("*");

        // 2. 프로젝트 접속자 확인
        registry.addHandler(projectPresenceController, "/ws/project/{projectId}")
                .addInterceptors(jwtHandshakeInterceptor) // ★ 토큰 검증
                .setAllowedOrigins("*");
    }

}
