package com.connecteamed.server.global.util;

import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.auth.exception.AuthException;
import com.connecteamed.server.global.auth.exception.code.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;
    private final MemberRepository memberRepository;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        SecurityUtil.applicationContext = context;
        log.info("[SecurityUtil] ApplicationContext initialized");
    }

    public static String getCurrentLoginId() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 실제 인증 정보가 있으면 그 정보 사용
        if (authentication != null && authentication.getName() != null &&
                !authentication.getName().equals("anonymousUser")) {
            String name = authentication.getName();
            log.info("[SecurityUtil] Using authenticated user: {}", name);
            return authentication.getName();
        }

        // 프로파일이 local로 지정되어 있을 때만 환경변수의 테스트 로그인 ID 반환
        String testLoginId = null;
        if (applicationContext != null) {

            //현재 프로파일이 local로 지정되어 있을 때만 허용
            boolean isTestableProfile = applicationContext.getEnvironment()
                    .acceptsProfiles(org.springframework.core.env.Profiles.of("local"));
            if(isTestableProfile) {
                testLoginId = applicationContext.getEnvironment().getProperty("app.test.login-id");
                if (testLoginId != null && !testLoginId.isEmpty()) {
                    log.info("[SecurityUtil] app.test.login-id from environment: {}", testLoginId);
                    log.info("[SecurityUtil] Using test login ID: {}", testLoginId);
                    return testLoginId;
                }
            }
        }else {
            log.warn("[SecurityUtil] ApplicationContext is null!");
        }

        // 3. 둘 다 해당하지 예외 발생
        throw new AuthException(AuthErrorCode.EMPTY_AUTHENTICATION);
    }

    public Long getCurrentMemberId() {
        String loginId = getCurrentLoginId();
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED, "인증된 사용자를 찾을 수 없습니다."))
                .getId();
    }
}