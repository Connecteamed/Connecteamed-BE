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

@Tag(name = "Contribution", description = "잔디 관련 API")
@RestController
@RequestMapping("/api/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionService contributionService;

    @GetMapping("/calendar")
    @Operation(summary = "연간 잔디 조회", description = "특정 연도의 1월 1일부터 12월 31일까지의 모든 잔디 데이터를 조회합니다.")
    public ApiResponse<CalendarContributionRes> getCalendar(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "year") int year
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, contributionService.getCalendar(userId, year));
    }
}