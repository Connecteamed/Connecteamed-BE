package com.connecteamed.server.domain.task.dto;

import java.time.Instant;
import java.util.List;

public record CompletedTaskListRes(
        List<TaskSummary> tasks
) {
    public record TaskSummary(
            Long taskId,
            String title,
            String contents,
            String status,
            Instant startDate,
            Instant endDate,
            List<AssigneeInfo> assignees,
            boolean isMine
    ) {}

    public record AssigneeInfo(
            Long id,
            String nickname
    ) {}
}
