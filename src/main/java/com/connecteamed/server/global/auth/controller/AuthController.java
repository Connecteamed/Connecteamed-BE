package com.connecteamed.server.global.auth.controller;


import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.auth.dto.AuthReqDTO;
import com.connecteamed.server.global.auth.dto.AuthResDTO;
import com.connecteamed.server.global.auth.exception.code.AuthSuccessCode;
import com.connecteamed.server.global.auth.service.AuthCommandService;
import com.connecteamed.server.global.auth.service.AuthQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthCommandService authCommandService;
    private final AuthQueryService authQueryService;

    // 회원가입
    @Operation(summary = "회원가입", description = "새로운 회원을 등록합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "회원가입 성공 예시",
                                    value = "{ \"status\": \"success\", \"data\": { \"memberId\": 1, \"name\": \"홍길동\" }, \"message\": \"회원가입이 성공적으로 완료되었습니다.\", \"code\": null }"
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (필드 누락 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (MEMBER_NOT_FOUND)")
    })
    @PostMapping("/api/auth/signup")
    public ApiResponse<AuthResDTO.JoinDTO> signUp(
            @RequestBody @Valid AuthReqDTO.JoinDTO dto
    ){
        return ApiResponse.onSuccess(AuthSuccessCode.SIGNUP_SUCCESS, authCommandService.signup(dto));
    }

    // 로그인
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인을 진행하고 토큰을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
            content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject(
                    name = "로그인 성공 예시",
                    value = "{ \"status\": \"success\", \"data\": { \"memberId\": 1,\"accessToken\": \"eyJhbGci...\", \"refreshToken\": \"eyJhbGci...\",\"grantType\": \"Bearer\",\"expiresIn\": 14400 }, \"message\": \"로그인이 완료되었습니다.\", \"code\": null }"
            )
    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (필드 누락 등)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "필드 누락 예시",
                                    value = "{ \"status\": \"error\", \"data\": null, \"message\": \"ID/비밀번호는 로그인에 필수 입력 값입니다.\", \"code\": \"COMMON400\" }"
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "비밀번호 불일치",
                                            value = "{ \"status\": \"error\", \"data\": null, \"message\": \"비밀번호가 일치하지 않습니다.\", \"code\": \"INVALID_PASSWORD\" }"
                                    ),
                                    @ExampleObject(
                                            name = "없는 아이디 조회",
                                            value = "{ \"status\": \"error\", \"data\": null, \"message\": \"해당 사용자를 찾지 못했습니다.\", \"code\": \"MEMBER_NOT_FOUND\" }"
                                    )
                            }
                    )),
    })
    @PostMapping("/api/auth/login")
    public ApiResponse<AuthResDTO.LoginDTO> login(
            @RequestBody @Valid AuthReqDTO.LoginDTO dto
    ){
        return ApiResponse.onSuccess(AuthSuccessCode.LOGIN_SUCCESS, authQueryService.login(dto));
    }

    //로그아웃
    @Operation(summary = "로그아웃", description = "토큰을 무효화하여 로그아웃을 처리합니다.")
    @Parameters({
            @Parameter(name = "Authorization", description = "Access Token (Bearer {token})", required = true, in = ParameterIn.HEADER),
            @Parameter(name = "Refresh-Token", description = "Refresh Token", required = true, in = ParameterIn.HEADER)
    })
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (필드 누락 등)")
    })
    @PostMapping("/api/auth/logout")
    public void logout() {
        // 실제 로직은 SecurityConfig의 LogoutHandler에서 처리
    }

    //토큰 재발급
    @Operation(summary = "토큰 재발급", description = "만료된 Access Token을 Refresh Token을 통해 재발급받습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "재발급 성공 예시",
                                    value = "{ \"status\": \"success\", \"data\": { \"accessToken\": \"eyJhbGci...\", \"refreshToken\": \"eyJhbGci...\" }, \"message\": \"재발급에 성공하였습니다.\", \"code\": null }"
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청(refreshToken 누락)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 토큰/토큰 만료"),
    })
    @PostMapping("/api/auth/refresh")
    public ApiResponse<AuthResDTO.RefreshResultDTO> reissue(@RequestBody @Valid AuthReqDTO.ReissueDTO request) {

        AuthResDTO.RefreshResultDTO result = authQueryService.reissue(request);

        return ApiResponse.onSuccess(AuthSuccessCode.REISSUE_SUCCESS, result);
    }


    @Operation(
            summary = "구글 로그인",
            description =
                    """
                            이 API는 `Try it out` 으로 테스트가 불가능합니다. 테스트 필요시 브라우저 주소창에 직접 입력하여 접근하세요.
                            
                            ---\s
                            Redirect URL: {FRONT_URL}/login/callback
                            
                            성공 시 전달 파라미터 (Redirect Query String)
                            | 파라미터명 | 타입 | 설명 | 예시 |
                            | :--- | :--- | :--- | :--- |
                            | **memberId** | Long | 사용자 식별자 | 1 |
                            | **accessToken** | String | API 인증용 토큰 | eyJhbGci... |
                            | **refreshToken** | String | 토큰 갱신용 토큰 | eyJhbGci... |
                            | **grantType** | String | 인증타입 | Bearer |
                            | **expiresIn** | Long | 만료 시간(초) | 14400 |
                            ---"""
    )
    @GetMapping("/api/auth/login/google")
    public void googleLogin() {}

    @Operation(
            summary = "카카오 로그인",
            description =
                    """
                            이 API는 `Try it out` 으로 테스트가 불가능합니다. 테스트 필요시 브라우저 주소창에 직접 입력하여 접근하세요.
                            
                            ---\s
                            Redirect URL: {FRONT_URL}/login/callback
                            
                            성공 시 전달 파라미터 (Redirect Query String)
                            | 파라미터명 | 타입 | 설명 | 예시 |
                            | :--- | :--- | :--- | :--- |
                            | **memberId** | Long | 사용자 식별자 | 1 |
                            | **accessToken** | String | API 인증용 토큰 | eyJhbGci... |
                            | **refreshToken** | String | 토큰 갱신용 토큰 | eyJhbGci... |
                            | **grantType** | String | 인증타입 | Bearer |
                            | **expiresIn** | Long | 만료 시간(초) | 14400 |
                            ---"""
    )
    @GetMapping("/api/auth/login/kakao")
    public void kakaoLogin() {}


}
