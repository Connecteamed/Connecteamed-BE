package com.connecteamed.server.domain.task.dto;

import java.time.Instant;
import java.util.List;

public record CompletedTaskDetailRes (
        Long taskId,
        String title,
        String status,
        List<Long> assigneeIds,
        Instant startDate,
        Instant endDate,
        String contents,
        String noteContent
) {}
