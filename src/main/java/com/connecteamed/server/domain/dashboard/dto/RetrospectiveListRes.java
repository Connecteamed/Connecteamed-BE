package com.connecteamed.server.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record RetrospectiveListRes (
        List<RetrospectiveRes> retrospectives
) {
    public record RetrospectiveRes (
            Long id,
            String title,
            String teamName,
            @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
            Instant writtenDate
    ) {}
}
