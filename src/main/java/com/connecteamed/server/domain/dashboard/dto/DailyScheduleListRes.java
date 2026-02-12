package com.connecteamed.server.domain.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record DailyScheduleListRes (
        Instant date,
        List<ScheduleRes> schedules
) {
    public record ScheduleRes (
            Long id,
            Long teamId,
            String title,
            String teamName,
            Instant time
    ) {}
}
