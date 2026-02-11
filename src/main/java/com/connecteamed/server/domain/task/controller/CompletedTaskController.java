package com.connecteamed.server.domain.task.controller;

import com.connecteamed.server.domain.task.dto.CompletedTaskDetailRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskListRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskStatusUpdateReq;
import com.connecteamed.server.domain.task.dto.CompletedTaskUpdateReq;
import com.connecteamed.server.domain.task.service.CompletedTaskService;
import com.connecteamed.server.global.apiPayload.ApiResponse;
import com.connecteamed.server.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Completed Task", description = "완료한 업무 목록/상세 조회 및 회고 관리 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CompletedTaskController {

    private final CompletedTaskService completedTaskService;

    @Operation(summary = "완료한 업무 목록 조회", description = "프로젝트 내 상태가 DONE인 업무 리스트를 반환합니다.")
    @GetMapping("/projects/{projectId}/tasks/completed")
    public ApiResponse<CompletedTaskListRes> getCompletedTasks(@PathVariable Long projectId) {
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, completedTaskService.getCompletedTasks(projectId));
    }

    @Operation(summary = "완료한 업무 상태 변경", description = "업무의 진행 상태를 변경합니다.")
    @PatchMapping("/tasks/{taskId}/status")
    public ApiResponse<String> updateTaskStatus(
            @PathVariable Long taskId,
            @RequestBody CompletedTaskStatusUpdateReq req) {
        completedTaskService.updateCompletedTaskStatus(taskId, req.status());
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, null,"업무 상태가 변경되었습니다.");
    }

    @Operation(summary = "완료한 업무 상세 조회", description = "업무 상세 정보와 작성한 회고 내용을 조회합니다.")
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<CompletedTaskDetailRes> getCompletedTaskDetail(@PathVariable Long taskId) {
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, completedTaskService.getCompletedTaskDetail(taskId));
    }

    @Operation(summary = "완료한 업무 상세 수정 및 회고 저장", description = "업무 정보 수정 및 회고를 저장합니다.")
    @PatchMapping("/tasks/{taskId}")
    public ApiResponse<CompletedTaskDetailRes> updateCompletedTask(
            @PathVariable Long taskId,
            @RequestBody CompletedTaskUpdateReq req) {
        CompletedTaskDetailRes result = completedTaskService.updateCompletedTask(taskId, req);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, result);
    }

    @Operation(summary = "완료한 업무 삭제", description = "업무를 Soft Delete 합니다.")
    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<String> deleteCompletedTask(@PathVariable Long taskId) {
        completedTaskService.deleteCompletedTask(taskId);
        return ApiResponse.onSuccess(GeneralSuccessCode._OK, null,"업무가 삭제되었습니다.");
    }
}
