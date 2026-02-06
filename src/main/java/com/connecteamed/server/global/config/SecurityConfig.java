package com.connecteamed.server.global.config;

import com.connecteamed.server.domain.token.repository.BlacklistedTokenRepository;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.auth.*;
import com.connecteamed.server.global.auth.exception.code.AuthErrorCode;
import com.connecteamed.server.global.auth.exception.code.AuthSuccessCode;
import com.connecteamed.server.global.util.FilterResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor // JwtUtil과 Service 주입을 위해 필요합니다.
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    //소셜 로그인용
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private final JwtLogoutHandler jwtLogoutHandler;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final FilterResponseUtils filterResponseUtils;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }



    // local: API 전체 오픈
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        configureCommonSecurity(http);
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                    "/", "/index.html", "/document.html",
                    "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error",
                    "/docs", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/api/**").permitAll()
                .anyRequest().permitAll()
            );

        return http.build();
    }

    // dev/prod: API 보호
    @Bean
    @Profile("!local")
    public SecurityFilterChain defaultFilterChain(HttpSecurity http, FilterResponseUtils filterResponseUtils) throws Exception {
        configureCommonSecurity(http);
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                    "/", "/index.html", "/document.html",
                    "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error",
                    "/docs", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**"
                ).permitAll()
                // 로그인/회원가입 같은 것만 예외로 오픈
                .requestMatchers("/api/auth/login","/login/oauth2/code/**","/api/auth/refresh","/api/auth/signup","/api/members/check-id").permitAll()
                .anyRequest().authenticated()
                )
                // Oauth2 관련 설정
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/api/auth/login")
                        )
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)) //
                        .successHandler(oAuth2AuthenticationSuccessHandler) //
                )
                // JWT 필터 추가
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, customUserDetailsService, blacklistedTokenRepository,filterResponseUtils),
                        org.springframework.security.web.authentication.logout.LogoutFilter.class)
                // 상세 로그아웃 설정
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler(jwtLogoutHandler)
                        .logoutSuccessHandler(customLogoutSuccessHandler())
                )
                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 토큰 없이 들어온 사람에게 동현님표 공통 응답 전송!
                            filterResponseUtils.sendErrorResponse(response, AuthErrorCode.EMPTY_AUTHENTICATION);
                        })
                )
        ;

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://api.connecteamed.shop",
                "https://connecteamed.shop",
                "https://connecteamed.vercel.app"

        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Refresh-Token", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setAllowCredentials(true); 
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    //공통 설정
    private void configureCommonSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 무조건 Stateless 유지
                );
    }


    //로그아웃 성공 핸들러
    private LogoutSuccessHandler customLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            response.setContentType("application/json;charset=UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();

            if (authentication == null) {
                filterResponseUtils.sendErrorResponse(response, AuthErrorCode.EMPTY_AUTHENTICATION);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_SUCCESS, null)));
        };
    }
}
