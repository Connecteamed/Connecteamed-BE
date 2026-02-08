package com.connecteamed.server.domain.meeting.dto;

import java.time.Instant;
import java.util.List;

public record MeetingDetailRes(
    Long meetingId,
    Long projectId,
    String title,
    String meetingDate,
    List<AgendaInfo> agendas,
    List<AttendeeInfo> attendees
) {
    public record AttendeeInfo(
            Long id,
            String nickname,
            String role
    ) {}

    public record AgendaInfo(
            Long agendaId,
            String title,
            String content
    ) {}
}
