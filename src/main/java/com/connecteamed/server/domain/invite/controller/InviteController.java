package com.connecteamed.server.domain.invite.controller;


import com.connecteamed.server.domain.invite.code.InviteSuccessCode;
import com.connecteamed.server.domain.invite.dto.InviteCodeRes;
import com.connecteamed.server.domain.invite.dto.ProjectJoinReq;
import com.connecteamed.server.domain.invite.dto.ProjectJoinRes;
import com.connecteamed.server.domain.invite.service.InviteService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로젝트 초대 API", description = "프로젝트 초대 코드 발급 및 입장 관련 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/invite")
public class InviteController {


    private final InviteService inviteService;

    @Operation(summary = "초대 코드 발급", description = "해당 프로젝트의 멤버인 경우 초대 코드를 조회하거나 새로 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "초대 코드 발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "inviteCode": "d699e1eb",
                                                 "expiredAt": "2026-01-24T15:49:44.062127Z"
                                              },
                                              "message": "초대 코드 발급에 성공하였습니다.",
                                              "code": null
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping("/{projectId}")
    public ApiResponse<InviteCodeRes> getInviteCode(
            @Parameter(description = "프로젝트 ID", example = "1")
            @PathVariable(name = "projectId") @Positive Long projectId
    ) {
        String loginId = SecurityUtil.getCurrentLoginId();
        InviteCodeRes response = inviteService.getOrGenerateInviteCode(projectId, loginId);
        return ApiResponse.onSuccess(InviteSuccessCode.INVITE_CODE_GENERATE_SUCCESS, response);
    }

    @Operation(summary = "초대 코드로 프로젝트 입장", description = "유효한 초대 코드를 입력하여 프로젝트의 새로운 팀원으로 합류합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "초대 코드 발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {"projectId":1},
                                              "message": "요청에 성공하였습니다.",
                                              "code": null
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/join")
    public ApiResponse<ProjectJoinRes> joinProject(
            @Valid @RequestBody ProjectJoinReq request
    ) {
        String loginId = SecurityUtil.getCurrentLoginId();

        ProjectJoinRes response = inviteService.joinProjectByCode(request.getInviteCode(), loginId);

// null 대신 response 전달
        return ApiResponse.onSuccess(InviteSuccessCode.INVITE_OK, response);
    }
}