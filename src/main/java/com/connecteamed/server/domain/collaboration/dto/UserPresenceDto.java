package com.connecteamed.server.domain.collaboration.dto;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPresenceDto {
    private String userId;
    private String userName;
    // 필요한 경우 profileImage, color 등 추가
    
    // Redis Set에서 중복 제거를 위해 equals/hashCode 필수 (userId 기준)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPresenceDto that = (UserPresenceDto) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
