package com.connecteamed.server.domain.meeting.service;

import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.meeting.dto.MeetingCreateReq;
import com.connecteamed.server.domain.meeting.dto.MeetingCreateRes;
import com.connecteamed.server.domain.meeting.dto.MeetingUpdateReq;
import com.connecteamed.server.domain.meeting.entity.Meeting;
import com.connecteamed.server.domain.meeting.repository.MeetingAgendaRepository;
import com.connecteamed.server.domain.meeting.repository.MeetingAttendeeRepository;
import com.connecteamed.server.domain.meeting.repository.MeetingRepository;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.SecurityUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingAgendaRepository meetingAgendaRepository;
    @Mock private MeetingAttendeeRepository meetingAttendeeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ContributionService contributionService;
    @Mock private SecurityUtil securityUtil;

    @InjectMocks private MeetingService meetingService;

    @Test
    @DisplayName("회의록 생성: 프로젝트 참조 후 저장하고 MEETING_CREATE 잔디를 기록한다")
    void createMeeting_success() {
        // given
        Long projectId = 1L;
        Long userId = 10L;

        var req = new MeetingCreateReq(
                projectId,
                "주간 회의",
                Instant.parse("2026-01-15T10:00:00Z"),
                List.of(new MeetingCreateReq.AgendaReq("안건1", "내용1", 1)),
                List.of(1L, 2L)
        );

        Project projectRef = mock(Project.class);
        ProjectMember memberRef = mock(ProjectMember.class);

        given(securityUtil.getCurrentMemberId()).willReturn(userId);

        // 프로젝트 멤버 존재 여부 확인 로직 대응 (validateProjectAccess 대응)
        given(projectMemberRepository.existsByProjectIdAndMemberId(projectId, userId)).willReturn(true);

        given(projectRepository.findById(projectId)).willReturn(Optional.of(projectRef));
        given(projectMemberRepository.findById(any())).willReturn(Optional.of(memberRef));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
            Meeting m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 100L);
            ReflectionTestUtils.setField(m, "createdAt", Instant.now());
            return m;
        });

        // when
        MeetingCreateRes res = meetingService.createMeeting(projectId, req);

        // then
        then(meetingRepository).should().save(any(Meeting.class));
        assertThat(res.meetingId()).isEqualTo(100L);
        verify(contributionService).recordContribution(eq(userId), any(ContributionReq.class));
    }

    @Test
    @DisplayName("회의록 수정: 기존 데이터 삭제 후 재등록 로직 검증")
    void updateMeeting_success() {
        // given
        Long meetingId = 100L;
        Long userId = 10L;
        Long projectId = 1L;

        Project project = mock(Project.class);
        given(project.getId()).willReturn(projectId);

        Meeting existingMeeting = Meeting.builder()
                .title("기존 제목")
                .project(project)
                .meetingDate(Instant.now())
                .build();
        ReflectionTestUtils.setField(existingMeeting, "id", meetingId);

        given(securityUtil.getCurrentMemberId()).willReturn(userId);
        given(projectMemberRepository.existsByProjectIdAndMemberId(projectId, userId)).willReturn(true);
        given(meetingRepository.findByIdAndDeletedAtIsNull(meetingId)).willReturn(Optional.of(existingMeeting));

        List<MeetingUpdateReq.UpdateAgendaInfo> emptyAgendas = List.of();
        var req = new MeetingUpdateReq("수정 제목", Instant.parse("2026-01-15T11:00:00Z"), emptyAgendas, List.of());

        // when
        meetingService.updateMeeting(meetingId, req);

        // then
        assertThat(existingMeeting.getTitle()).isEqualTo("수정 제목");
        verify(contributionService).recordContribution(eq(userId), any(ContributionReq.class));
    }

    @Test
    @DisplayName("회의록 수정: 참석자 초기화 후 flush가 호출되는지 확인")
    void updateMeeting_flush_check() {
        // given
        Long meetingId = 100L;
        Long userId = 10L;
        Long projectId = 1L;

        Project mockProject = mock(Project.class);
        ProjectMember mockProjectMember = mock(ProjectMember.class);
        Member mockMember = mock(Member.class);

        lenient().when(mockProject.getId()).thenReturn(projectId);
        lenient().when(mockProjectMember.getId()).thenReturn(1L);
        lenient().when(mockProjectMember.getMember()).thenReturn(mockMember);
        lenient().when(mockMember.getId()).thenReturn(1L);
        lenient().when(mockMember.getName()).thenReturn("테스터");
        lenient().when(mockProjectMember.getRoles()).thenReturn(List.of()); // roles 리스트 비어있음 설정

        Meeting existingMeeting = Meeting.builder()
                .title("기존")
                .project(mockProject)
                .meetingDate(Instant.now())
                .build();
        ReflectionTestUtils.setField(existingMeeting, "id", meetingId);

        given(securityUtil.getCurrentMemberId()).willReturn(userId);
        given(projectMemberRepository.existsByProjectIdAndMemberId(projectId, userId)).willReturn(true);

        var req = new MeetingUpdateReq("수정", Instant.now(), List.of(), List.of(1L));

        given(meetingRepository.findByIdAndDeletedAtIsNull(meetingId)).willReturn(Optional.of(existingMeeting));
        given(projectMemberRepository.findById(1L)).willReturn(Optional.of(mockProjectMember));

        // when
        meetingService.updateMeeting(meetingId, req);

        // then
        then(meetingRepository).should().flush();
        assertThat(existingMeeting.getTitle()).isEqualTo("수정");
    }

    @Test
    @DisplayName("회의록 상세 조회: 존재하지 않는 ID 조회 시 예외 발생")
    void getMeeting_fail_notFound() {
        // given
        Long invalidId = 999L;
        given(meetingRepository.findByIdAndDeletedAtIsNull(invalidId)).willReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> meetingService.getMeeting(invalidId))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("code", GeneralErrorCode.NOT_FOUND);

        verify(contributionService, never()).recordContribution(any(), any());
    }

    @Test
    @DisplayName("회의록 삭제: deletedAt 필드에 시간이 기록되는지 확인")
    void deleteMeeting_success() {
        // given
        Long meetingId = 100L;
        Long userId = 10L;
        Long projectId = 1L;

        Project mockProject = mock(Project.class);
        given(mockProject.getId()).willReturn(projectId);

        Meeting existingMeeting = Meeting.builder()
                .title("삭제할 회의")
                .project(mockProject)
                .build();
        ReflectionTestUtils.setField(existingMeeting, "id", meetingId);

        given(securityUtil.getCurrentMemberId()).willReturn(userId);
        given(projectMemberRepository.existsByProjectIdAndMemberId(projectId, userId)).willReturn(true);
        given(meetingRepository.findByIdAndDeletedAtIsNull(meetingId)).willReturn(Optional.of(existingMeeting));

        // when
        meetingService.deleteMeeting(meetingId);

        // then
        assertThat(existingMeeting.getDeletedAt()).isNotNull();
    }
}