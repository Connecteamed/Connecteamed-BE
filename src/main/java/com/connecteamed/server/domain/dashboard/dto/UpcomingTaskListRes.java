package com.connecteamed.server.domain.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record UpcomingTaskListRes (
        List<UpcomingTaskRes> tasks
) {
    public record UpcomingTaskRes (
            Long id,
            String title,
            String teamName,
            Instant endDate
    ) {}
}
