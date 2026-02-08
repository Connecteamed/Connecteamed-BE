package com.connecteamed.server.domain.collaboration.service;

import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.connecteamed.server.domain.collaboration.dto.UserPresenceDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 프로젝트 접속자 키: presence:project:{projectId}
    // 문서 접속자 키: presence:doc:{docId}

    public void addUser(String type, String id, UserPresenceDto user) {
        String key = "presence:" + type + ":" + id;
        redisTemplate.opsForSet().add(key, user);
    }

    public void removeUser(String type, String id, UserPresenceDto user) {
        String key = "presence:" + type + ":" + id;
        redisTemplate.opsForSet().remove(key, user);
    }

    public Set<Object> getUsers(String type, String id) {
        String key = "presence:" + type + ":" + id;
        return redisTemplate.opsForSet().members(key);
    }
}
