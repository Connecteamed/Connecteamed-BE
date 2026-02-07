package com.connecteamed.server.global.auth;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.enums.SocialType;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

//spring에서 access Token 얻은 후 사용자 정보를 얻기 위해 자동으로 호출하는 로직
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {


    private final MemberRepository memberRepository;



    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email;
        String name;
        SocialType socialType;


        if ("kakao".equals(registrationId)) {
            // 카카오 로그인인 경우 데이터 추출
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            email = (String) kakaoAccount.get("email");
            name = (String) profile.get("nickname"); // 실명 대신 닉네임 활용
            socialType = SocialType.KAKAO;
        } else if ("google".equals(registrationId)) { // 2. 구글 명시적 처리
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
            socialType = SocialType.GOOGLE;

        } else {
            // 지원하지 않는 소셜 로그인 공급자
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인 공급자입니다: " + registrationId);
        }

        if (email == null) {
            throw new OAuth2AuthenticationException("소셜 서비스로부터 이메일 정보를 불러올 수 없습니다.");
        }

        SocialType finalSocialType = socialType;
        Member member = memberRepository.findByLoginId(email)
                .orElseGet(() -> registerNewMember(email, name, finalSocialType));

        return new CustomUserDetails(member, attributes);
    }

    private Member registerNewMember(String email, String name, SocialType socialType) {
        // 소셜 로그인 유저는 loginId에 email을 할당하여 기존 로직과 호환시킴
        Member newMember = Member.builder()
                .loginId(email)       // 아이디를 이메일로 대체
                .name(name)
                .socialType(socialType)
                .build();

        return memberRepository.save(newMember);
    }

}
