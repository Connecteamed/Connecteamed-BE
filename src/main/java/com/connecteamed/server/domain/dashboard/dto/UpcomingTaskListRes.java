package com.connecteamed.server.domain.dashboard.dto;

import com.connecteamed.server.domain.task.enums.TaskStatus;

import java.time.Instant;
import java.util.List;

public record UpcomingTaskListRes (
        List<UpcomingTaskRes> tasks
) {
    public record UpcomingTaskRes (
            Long id,
            Long teamId,
            String title,
            TaskStatus status,
            String teamName,
            Instant endDate
    ) {}
}
