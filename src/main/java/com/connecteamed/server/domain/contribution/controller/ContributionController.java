package com.connecteamed.server.domain.contribution.controller;

import com.connecteamed.server.domain.contribution.code.ContributionSuccessCode;
import com.connecteamed.server.domain.contribution.dto.*;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.contribution.service.ProjectContributionService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Contribution", description = "잔디 관련 API")
@RestController
@RequestMapping("/api/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionService contributionService;
    private final ProjectContributionService projectContributionService;

    @GetMapping("/calendar")
    @Operation(summary = "연간 잔디 조회", description = "특정 연도의 1월 1일부터 12월 31일까지의 모든 잔디 데이터를 조회합니다.")
    public ApiResponse<CalendarContributionRes> getCalendar(
            @RequestParam(name = "projectId") Long projectId,
            @RequestParam(name = "year") int year
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, contributionService.getCalendar(projectId, year));
    }


    @GetMapping("/{projectId}/individual")
    @Operation(
            summary = "프로젝트 팀원별 업무 통계(잔디) 조회",
            description = "특정 프로젝트에 속한 모든 팀원의 최근 4개월간 잔디 데이터를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "팀원별 잔디 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "조회 성공 예시",
                                    value = """
                    {
                      "status": "success",
                      "data": [
                        {
                          "id": 1,
                          "name": "member1",
                          "contributions": [
                            { "date": "2026-02-10", "count": 10, "level": 4 },
                            { "date": "2026-02-09", "count": 2, "level": 1 },
                            "이후로 계속 이어짐.."
                          ]
                        },
                        {
                          "id": 3,
                          "name": "member2",
                          "contributions": [
                            { "date": "2026-02-10", "count": 0, "level": 0 },
                            { "date": "2026-02-09", "count": 2, "level": 1 }
                          ]
                        }
                      ],
                      "message": "요청에 성공하였습니다.",
                      "code" : null
                    }
                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "팀을 찾을 수 없음(미존재)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "팀 미존재 에러 예시",
                                    value = """
                    {
                      "status": "error",
                      "data" : null,
                      "code": "CONTRIBUTION_PROJECT_NOT_FOUND",
                      "message": "해당 Id의 프로젝트를 찾을 수 없습니다."
                    }
                    """
                            )
                    )
            )
    })
    public ApiResponse<List<ProjectMemberContributionRes>> getIndividualContributions(
            @PathVariable Long projectId
    ) {
        List<ProjectMemberContributionRes> response = projectContributionService.getProjectMemberContributions(projectId);

        return ApiResponse.onSuccess(ContributionSuccessCode.CONTRIBUTION_OK, response);
    }


    @GetMapping("/{projectId}/entire")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "팀 전체 통계 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "조회 성공 예시",
                                    value = """
                    {
                      "status": "success",
                      "data": [
                        { "date": "2026-01-28", "count": 45 },
                        { "date": "2026-01-29", "count": 32 },
                        { "date": "2026-01-30", "count": 4 },
                        { "date": "2026-01-31", "count": 32 },
                        { "date": "2026-02-01", "count": 5 },
                        { "date": "2026-02-02", "count": 2 },
                        { "date": "2026-02-03", "count": 4 },
                        { "date": "2026-02-04", "count": 32 },
                        { "date": "2026-02-05", "count": 45 },
                        { "date": "2026-02-06", "count": 32 },
                        { "date": "2026-02-07", "count": 15 },
                        { "date": "2026-02-08", "count": 32 },
                        { "date": "2026-02-09", "count": 45 },
                        { "date": "2026-02-10", "count": 2 }
                      ],
                      "message": "요청에 성공하였습니다.",
                      "code": null
                    }
                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "팀을 찾을 수 없음(미존재)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "팀 미존재 에러 예시",
                                    value = """
                    {
                      "status": "error",
                      "data" : null,
                      "code": "CONTRIBUTION_PROJECT_NOT_FOUND",
                      "message": "해당 Id의 프로젝트를 찾을 수 없습니다."
                    }
                    """
                            )
                    )
            )
    })
    @Operation(
            summary = "팀 전체 업무 통계 조회",
            description = "최근 2주간 프로젝트 전체 멤버의 일별 기여도 합산을 반환합니다."
    )
    public ApiResponse<List<ProjectContributionRes>> getEntireContributions(
            @PathVariable Long projectId
    ) {
        return ApiResponse.onSuccess(ContributionSuccessCode.CONTRIBUTION_OK, projectContributionService.getEntireContributions(projectId));
    }



}