package com.connecteamed.server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.connecteamed.server.domain.collaboration.controller.CollabSocketController;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CollabSocketController collabSocketController;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 엔드포인트: ws://localhost:8080/ws/docs/{docId}
        registry.addHandler(collabSocketController, "/ws/docs/*")
                .setAllowedOrigins("*"); // 개발용 CORS 전체 허용
    }
}
