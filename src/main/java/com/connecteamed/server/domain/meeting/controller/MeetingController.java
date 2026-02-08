package com.connecteamed.server.domain.meeting.controller;

import com.connecteamed.server.domain.meeting.dto.*;
import com.connecteamed.server.domain.meeting.service.MeetingService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Meeting", description = "회의록 생성, 수정, 조회 및 삭제 API")
@RequestMapping("/api")
public class MeetingController {

    private final MeetingService meetingService;

    @Operation(summary = "회의록 생성", description = "특정 프로젝트 내에 새로운 회의록을 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/projects/{projectId}/meetings")
    public ApiResponse<MeetingCreateRes> createMeeting(
            @Parameter(description = "프로젝트 식별자") @PathVariable Long projectId,
            @jakarta.validation.Valid @RequestBody MeetingCreateReq request) {

        MeetingCreateRes result = meetingService.createMeeting(projectId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode._CREATED, result);
    }

    @Operation(summary = "회의록 수정")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PatchMapping("/meetings/{meetingId}")
    public ApiResponse<MeetingDetailRes> updateMeeting(
            @Parameter(description = "회의록 식별자") @PathVariable Long meetingId,
            @jakarta.validation.Valid @RequestBody MeetingUpdateReq request){

        MeetingDetailRes result = meetingService.updateMeeting(meetingId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, result);
    }

    @Operation(summary = "회의록 목록 조회")
    @GetMapping("/projects/{projectId}/meetings")
    public ApiResponse<MeetingListRes> getMeetings(@PathVariable Long projectId) {
        MeetingListRes result = meetingService.getMeetings(projectId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, result);
    }

    @Operation(summary = "회의록 상세 조회")
    @GetMapping("/meetings/{meetingId}")
    public ApiResponse<MeetingDetailRes> getMeeting(@PathVariable Long meetingId) {

        MeetingDetailRes result = meetingService.getMeeting(meetingId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, result);
    }

    @Operation(summary = "회의록 삭제")
    @DeleteMapping("/meetings/{meetingId}")
    public ApiResponse<String> deleteMeeting(@PathVariable Long meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, "회의록이 성공적으로 삭제되었습니다.");
    }
}