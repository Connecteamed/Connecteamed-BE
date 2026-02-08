package com.connecteamed.server.domain.auth.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.enums.SocialType;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.global.auth.CustomOAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private RestOperations restOperations;

    @Test
    @DisplayName("카카오 유저 정보를 받으면 DB에 KAKAO 타입으로 저장되어야 한다")
    void kakao_login_test() {

        // RestOperations 주입
        customOAuth2UserService.setRestOperations(restOperations);

        //  액세스 토큰 생성
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("kakao")
                .clientId("test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("test")
                .authorizationUri("test")
                .tokenUri("test")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .build();

        OAuth2UserRequest userRequest = new OAuth2UserRequest(clientRegistration, accessToken);


        Map<String, Object> attributes = Map.of(
                "id", 12345L,
                "kakao_account", Map.of(
                        "email", "test@kakao.com",
                        "profile", Map.of("nickname", "테스트닉네임")
                )
        );

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(attributes, HttpStatus.OK);

        given(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
                .willReturn(responseEntity);

        // 신규 회원으로 가정
        given(memberRepository.findByLoginId("test@kakao.com")).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        customOAuth2UserService.loadUser(userRequest);

        //1. social type kakao로 저장되는지, 2. 로그인 Id 로직대로 저장되는지 확인
        verify(memberRepository).save(argThat(member ->
                member.getSocialType() == SocialType.KAKAO &&
                        member.getLoginId().equals("test@kakao.com")
        ));
    }



    @Test
    @DisplayName("구글 유저 정보를 받으면 DB에 GOOGLE 타입으로 저장되어야 한다")
    void google_login_test() {
        customOAuth2UserService.setRestOperations(restOperations);

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-google-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2UserRequest userRequest = new OAuth2UserRequest(clientRegistration, accessToken);

        Map<String, Object> attributes = Map.of(
                "sub", "google-unique-id-123",
                "email", "test@google.com",
                "name", "구글유저"
        );

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(attributes, HttpStatus.OK);

        given(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
                .willReturn(responseEntity);

        given(memberRepository.findByLoginId("test@google.com")).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));


        customOAuth2UserService.loadUser(userRequest);

        //SocialType.GOOGLE로 잘 저장되는지 확인
        verify(memberRepository).save(argThat(member ->
                member.getSocialType() == SocialType.GOOGLE &&
                        member.getLoginId().equals("test@google.com") &&
                        member.getName().equals("구글유저")
        ));
    }
}
