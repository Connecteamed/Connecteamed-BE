package com.connecteamed.server.domain.collaboration.interceptor;

import java.util.Map;

// ★ [중요] reactive가 붙은 import를 지우고 아래 것으로 교체하세요!
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.connecteamed.server.domain.collaboration.dto.UserPresenceDto;
import com.connecteamed.server.global.auth.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            // 1. URL에서 token 파라미터 추출
            String query = servletRequest.getServletRequest().getQueryString();
            String token = extractToken(query);

            // 2. 토큰 검증
            if (token != null && jwtUtil.isValid(token)) {
                try {
                    String userId = jwtUtil.getUserId(token);
                    
                    if (userId != null) {
                        String userName = userId; 
                        UserPresenceDto user = new UserPresenceDto(userId, userName);
                        
                        attributes.put("user", user);
                        return true;
                    }
                } catch (Exception e) {
                    log.error("Token validation failed", e);
                }
            }
        }
        return false;
    }

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && "token".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                               WebSocketHandler wsHandler, Exception exception) {
        // 후처리가 필요 없으면 비워둡니다.
    }
}
