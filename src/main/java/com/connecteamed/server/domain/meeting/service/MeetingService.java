package com.connecteamed.server.domain.meeting.service;

import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.meeting.dto.*;
import com.connecteamed.server.domain.meeting.entity.Meeting;
import com.connecteamed.server.domain.meeting.entity.MeetingAgenda;
import com.connecteamed.server.domain.meeting.entity.MeetingAttendee;
import com.connecteamed.server.domain.meeting.repository.MeetingAgendaRepository;
import com.connecteamed.server.domain.meeting.repository.MeetingAttendeeRepository;
import com.connecteamed.server.domain.meeting.repository.MeetingRepository;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ContributionService  contributionService;
    private final SecurityUtil securityUtil;

    // 회의록 생성
    @Transactional
    public MeetingCreateRes createMeeting(Long projectId, MeetingCreateReq request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        validateProjectAccess(projectId);

        // Meeting 객체를 먼저 생성
        Meeting meeting = Meeting.builder()
                .project(project)
                .title(request.title())
                .meetingDate(request.meetingDate())
                .build();

        // 안건 추가
        if (request.agendas() != null) {
            request.agendas().forEach(agendaReq -> {
                meeting.getAgendas().add(MeetingAgenda.builder()
                        .meeting(meeting)
                        .title(agendaReq.title())
                        .content(agendaReq.content())
                        .sortOrder(agendaReq.sortOrder())
                        .build());
            });
        }

        // 참석자 추가
        if (request.attendeeMemberIds() != null) {
            request.attendeeMemberIds().forEach(memberId -> {
                ProjectMember member = projectMemberRepository.findById(memberId)
                        .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
                meeting.addAttendee(member);
            });
        }

        // 마지막에 저장
        Meeting savedMeeting = meetingRepository.save(meeting);

        Long userId = securityUtil.getCurrentMemberId();
        contributionService.recordContribution(userId,
                new ContributionReq(ContributionAction.MEETING_CREATE, savedMeeting.getId()));

        return new MeetingCreateRes(savedMeeting.getId(), savedMeeting.getCreatedAt());
    }
    // 회의록 수정
    @Transactional
    public MeetingDetailRes updateMeeting(Long meetingId, MeetingUpdateReq request) {
        Meeting meeting = meetingRepository.findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        Long userId = securityUtil.getCurrentMemberId();
        if (!projectMemberRepository.existsByProjectIdAndMemberId(meeting.getProject().getId(), userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "회의록 수정 권한이 없습니다.");
        }

        // 기본 정보 업데이트
        meeting.update(request.title(), request.meetingDate());

        // 안건 업데이트
        List<MeetingAgenda> existingAgendas = meeting.getAgendas();
        List<Long> requestAgendaIds = request.agendas().stream()
                .map(MeetingUpdateReq.UpdateAgendaInfo::id)
                .filter(java.util.Objects::nonNull)
                .toList();

        existingAgendas.removeIf(agenda -> !requestAgendaIds.contains(agenda.getId()));

        request.agendas().forEach(agendaDto -> {
            if (agendaDto.id() != null) {
                existingAgendas.stream()
                        .filter(a -> a.getId().equals(agendaDto.id()))
                        .findFirst()
                        .ifPresent(a -> {
                            a.update(agendaDto.title(), agendaDto.content(), agendaDto.sortOrder());
                        });
            } else {
                meeting.getAgendas().add(MeetingAgenda.builder()
                        .meeting(meeting)
                        .title(agendaDto.title())
                        .content(agendaDto.content())
                        .sortOrder(agendaDto.sortOrder())
                        .build());
            }
        });

        // 참석자 업데이트
        meeting.getAttendees().clear();
        meetingRepository.flush();
        if (request.attendeeIds() != null) {
            request.attendeeIds().forEach(memberId -> {
                ProjectMember member = projectMemberRepository.findById(memberId)
                        .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
                meeting.addAttendee(member);
            });
        }

        contributionService.recordContribution(userId,
                new ContributionReq(ContributionAction.MEETING_UPDATE, meeting.getId()));
        return getMeeting(meetingId);
    }

    // 회의록 상세 조회
    public MeetingDetailRes getMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        validateProjectAccess(meeting.getProject().getId());

        return new MeetingDetailRes(
                meeting.getId(),
                meeting.getProject().getId(),
                meeting.getTitle(),
                meeting.getMeetingDate().toString().replace("-", "."),
                meeting.getAgendas().stream().map(a -> new MeetingDetailRes.AgendaInfo(
                        a.getId(), a.getTitle(), a.getContent()
                )).toList(),
                meeting.getAttendees().stream().map(at -> {
                    ProjectMember pm = at.getAttendee();
                    String roleName = pm.getRoles().stream()
                            .findFirst()
                            .map(pmr -> pmr.getRole().getRoleName())
                            .orElse("");

                    return new MeetingDetailRes.AttendeeInfo(
                            pm.getMember().getId(),
                            pm.getMember().getName(),
                            roleName
                    );
                }).toList()
        );
    }
    // 4. 회의록 목록 조회
    public MeetingListRes getMeetings(Long projectId) {
        validateProjectAccess(projectId);

        List<Meeting> meetings = meetingRepository.findAllByProjectIdAndDeletedAtIsNull(projectId);

        return new MeetingListRes(
                meetings.stream().map(m -> new MeetingListRes.MeetingSummary(
                        m.getId(),
                        m.getTitle(),
                        m.getMeetingDate(),
                        m.getAttendees().stream().map(at -> new MeetingListRes.AttendeeSummary(
                                at.getAttendee().getId(),
                                at.getAttendee().getMember().getName()
                        )).toList()
                )).toList()
        );
    }

    // 5. 회의록 삭제
    @Transactional
    public void deleteMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        meeting.delete();
    }

    private void validateProjectAccess(Long projectId) {
        if (!projectMemberRepository.existsByProjectIdAndMemberId(projectId, securityUtil.getCurrentMemberId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 프로젝트에 대한 접근 권한이 없습니다.");
        }

    }
}
