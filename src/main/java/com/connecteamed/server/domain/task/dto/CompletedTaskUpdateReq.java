package com.connecteamed.server.domain.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CompletedTaskUpdateReq (
        @NotBlank(message = "업무 이름은 비워둘 수 없습니다.")
        String title,

        @NotNull(message = "상태값은 필수입니다.")
        String status,

        @NotNull(message = "담당자 ID 리스트는 null일 수 없습니다.")
        List<Long> assigneeIds,

        @NotBlank(message = "시작일은 필수입니다.")
        String startDate,

        @NotBlank(message = "종료일은 필수입니다.")
        String endDate,

        @NotNull(message = "업무 내용은 null일 수 없습니다.")
        String contents,

        @NotNull(message = "개인 회고 내용은 null일 수 없습니다.")
        String noteContent
) {}