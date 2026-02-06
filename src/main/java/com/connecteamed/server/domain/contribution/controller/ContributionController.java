package com.connecteamed.server.domain.contribution.controller;

import com.connecteamed.server.domain.contribution.dto.CalendarContributionRes;
import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.dto.ContributionRes;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Contribution API", description = "잔디 관련 API")
@RestController
@RequestMapping("/api/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionService contributionService;

    @PostMapping
    @Operation(summary = "활동 기록 등록", description = "업무 완료, 회의 생성 등 유저의 활동을 기록하고 오늘의 잔디 상태를 반환합니다.")
    public ApiResponse<ContributionRes> recordContribution(
            @AuthenticationPrincipal Long userId,
            @RequestBody ContributionReq request
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, contributionService.recordContribution(userId, request));
    }

    @GetMapping("/calendar")
    @Operation(summary = "연간 잔디 조회", description = "특정 연도의 1월 1일부터 12월 31일까지의 모든 잔디 데이터를 조회합니다.")
    public ApiResponse<CalendarContributionRes> getCalendar(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "year") int year
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, contributionService.getCalendar(userId, year));
    }
}