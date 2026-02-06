package com.connecteamed.server.global.auth;


import com.connecteamed.server.domain.member.entity.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public record CustomUserDetails(Member member,
                                Map<String, Object> attributes)
        implements UserDetails, OAuth2User {


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 모든 유저에게 "USER"라는 기본 권한 부여.
        // 나중에 관리자 기능이 필요해지면 엔티티에 필드를 추가
        return Collections.singletonList(new SimpleGrantedAuthority("USER"));
    }

    // 기존 자체 로그인용 생성자 (attributes를 빈 값으로 넘김)
    public CustomUserDetails(Member member) {
        this(member, java.util.Collections.emptyMap());
    }

    //소셜 로그인 용
    @Override
    public Map<String, Object> getAttributes() { return attributes; }

    //소셜 로그인 용
    @Override
    public String getName() { return member.getLoginId(); }

    @Override
    public String getPassword() {
        return member.getPassword();
    }

    @Override
    public String getUsername() {
        // 이메일이 아닌 로그인 ID(또는 식별값)를 반환하도록 설정
        return member.getLoginId();
    }

    // 아래 계정 상태 값들은 우선 모두 true로 설정합니다.
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}