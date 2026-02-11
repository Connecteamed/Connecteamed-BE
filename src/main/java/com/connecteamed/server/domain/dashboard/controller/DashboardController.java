package com.connecteamed.server.domain.dashboard.controller;

import com.connecteamed.server.domain.dashboard.dto.DailyScheduleListRes;
import com.connecteamed.server.domain.dashboard.dto.DashboardRes;
import com.connecteamed.server.domain.dashboard.dto.NotificationListRes;
import com.connecteamed.server.domain.dashboard.dto.UpcomingTaskListRes;
import com.connecteamed.server.domain.dashboard.service.DashboardService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "대시보드 관련 API")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 최근 회고 목록 조회
     * 로그인 사용자가 작성한 회고만 최근순으로 조회
     * @param authentication 로그인한 사용자 정보 (JWT 인증 시 사용)
     * @param username 개발 환경 테스트용 사용자 ID (선택)
     * @return 회고 목록 응답
     */
    @GetMapping("/retrospectives/recent")
    @Operation(
            summary = "최근 회고 목록 조회",
            description = "로그인한 사용자가 작성한 최근 회고 목록을 조회합니다. 개발 환경에서는 username 파라미터로 테스트 가능합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "회고 목록 조회 성공"
            )
    })
    public ApiResponse<DashboardRes.RetrospectiveListRes> getRecentRetrospectives(
            Authentication authentication,
            @Parameter(description = "개발 환경 테스트용 사용자 로그인 ID (예: user@example.com)", example = "writer@example.com")
            @RequestParam(required = false) String username
    ) {

        String userId = getUserId(authentication, username);

        DashboardRes.RetrospectiveListRes response = (userId != null)
                ? dashboardService.getRecentRetrospectives(userId)
                : dashboardService.getRecentRetrospectives();

        return ApiResponse.onSuccess(
                GeneralSuccessCode._OK,
                response,
                "회고 목록 조회에 성공했습니다"
        );
    }

    @GetMapping("/tasks/upcoming")
    @Operation(
            summary = "다가오는 업무 조회",
            description = "로그인한 사용자의 마감 임박 업무를 조회합니다."
    )
    public ApiResponse<UpcomingTaskListRes> getUpcomingTasks(
            Authentication authentication,
            @RequestParam(required = false) String username
    ) {
        String userId = getUserId(authentication, username);
        UpcomingTaskListRes response = dashboardService.getUpcomingTasks(userId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, response, "다가오는 업무 조회에 성공했습니다");
    }

    @GetMapping("/notifications/recent")
    @Operation(summary = "알림 조회", description = "로그인한 사용자의 최근 알림 목록을 조회합니다.")
    public ApiResponse<NotificationListRes> getRecentNotifications(
            Authentication authentication,
            @RequestParam(required = false) String username
    ) {
        String userId = getUserId(authentication, username);
        NotificationListRes response = dashboardService.getRecentNotifications(userId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, response, "알림 목록 조회에 성공했습니다");
    }

    @GetMapping("/schedules/daily")
    @Operation(summary = "날짜별 업무 조회", description = "특정 날짜의 업무 및 일정을 조회합니다.")
    public ApiResponse<DailyScheduleListRes> getDailySchedules(
            @Parameter(description = "조회 날짜", example = "2026-01-22T00:00:00Z")
            @RequestParam("date") Instant date,
            Authentication authentication,
            @RequestParam(required = false) String username
    ) {
        String userId = getUserId(authentication, username);
        DailyScheduleListRes response = dashboardService.getDailySchedules(userId, date);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, response, "날짜별 업무 조회에 성공했습니다");
    }

    private String getUserId(Authentication authentication, String username) {
        return (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName()))
                ? authentication.getName() : username;
    }
}

