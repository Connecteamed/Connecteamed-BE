package com.connecteamed.server.domain.meeting.dto;

import java.time.Instant;
import java.util.List;

public record MeetingCreateReq (
        Long projectId,
        String title,
        Instant meetingDate,
        List<AgendaReq> agendas,
        List<Long> attendeeMemberIds
) {
    public record AgendaReq(
            String title,
            String content,
            Integer sortOrder
    ) {}
}