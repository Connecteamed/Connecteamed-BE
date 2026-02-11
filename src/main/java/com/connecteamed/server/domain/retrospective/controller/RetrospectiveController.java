package com.connecteamed.server.domain.retrospective.controller;

import com.connecteamed.server.domain.retrospective.dto.*;
import com.connecteamed.server.domain.retrospective.service.RetrospectiveService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.apiPayload.code.GeneralSuccessCode;
import com.connecteamed.server.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Retrospective", description = "AI 회고 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/retrospectives")
public class RetrospectiveController {

    private final RetrospectiveService retrospectiveService;

    @Operation(summary = "AI 회고 생성 요청", description = "사용자의 성과와 선택한 업무를 바탕으로 AI 회고를 생성합니다.")
    @PostMapping("/ai")
    public ApiResponse<RetrospectiveCreateRes> createRetrospective(
            @PathVariable Long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody RetrospectiveCreateReq request
    ) {
        Long memberId = customUserDetails.member().getId();
        RetrospectiveCreateRes response = retrospectiveService.createAiRetrospective(projectId, memberId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode._CREATED, response);
    }

    @Operation(summary = "AI 회고 목록 조회", description = "특정 프로젝트의 모든 AI 회고 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<RetrospectiveListRes> getRetrospectiveList(
            @PathVariable Long projectId
    ) {
        RetrospectiveListRes response = retrospectiveService.getRetrospectivesByProject(projectId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, response);
    }

    @Operation(summary = "AI 회고 상세 조회", description = "생성된 AI 회고의 상세 내용을 조회합니다.")
    @GetMapping("/{retrospectiveId}")
    public ApiResponse<RetrospectiveDetailRes> getRetrospectiveDetail(
            @PathVariable Long projectId,
            @PathVariable Long retrospectiveId
    ) {
        RetrospectiveDetailRes response = retrospectiveService.getRetrospectiveDetail(projectId, retrospectiveId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, response);
    }

    @Operation(summary = "AI 회고 수정", description = "생성된 AI 회고의 제목이나 내용을 수정합니다.")
    @PatchMapping("/{retrospectiveId}")
    public ApiResponse<String> updateRetrospective(
            @PathVariable Long projectId,
            @PathVariable Long retrospectiveId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody RetrospectiveUpdateReq request
    ) {
        retrospectiveService.updateRetrospective(customUserDetails.member().getId(), projectId, retrospectiveId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, "회고가 성공적으로 수정되었습니다.");
    }

    @Operation(summary = "AI 회고 삭제", description = "생성된 AI 회고를 삭제합니다.")
    @DeleteMapping("/{retrospectiveId}")
    public ApiResponse<String> deleteRetrospective(
            @PathVariable Long projectId,
            @PathVariable Long retrospectiveId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        retrospectiveService.deleteRetrospective(customUserDetails.member().getId(), projectId, retrospectiveId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, "회고가 성공적으로 삭제되었습니다.");
    }
}
