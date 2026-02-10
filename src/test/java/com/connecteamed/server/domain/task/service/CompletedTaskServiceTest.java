package com.connecteamed.server.domain.task.service;

import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.service.NotificationCommandService;
import com.connecteamed.server.domain.notification.service.NotificationHelper;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.task.dto.CompletedTaskDetailRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskUpdateReq;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.entity.TaskAssignee;
import com.connecteamed.server.domain.task.entity.TaskNote;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.repository.TaskAssigneeRepository;
import com.connecteamed.server.domain.task.repository.TaskNoteRepository;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import com.connecteamed.server.global.util.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.BDDMockito.*;
import static org.assertj.core.api.Assertions.*;

import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompletedTaskServiceTest {

    @InjectMocks
    private CompletedTaskService completedTaskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskAssigneeRepository taskAssigneeRepository;

    @Mock
    private TaskNoteRepository taskNoteRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ContributionService contributionService;

    @Mock
    private NotificationHelper notificationHelper;

    @Mock
    private ProjectMemberRepository projectMemberRepository;


    @Test
    @DisplayName("완료 업무 상태 변경 시 잔디 기록 및 재시작 알림 발송 검증")
    void updateCompletedTaskStatus_Success() {
        try (MockedStatic<SecurityUtil> mockedSecurityUtil = mockStatic(SecurityUtil.class)) {
            // given
            Long taskId = 1L;
            Long memberId = 10L;
            String loginId = "testUser";

            Task task = Task.builder().id(taskId).status(TaskStatus.DONE).build();
            Member member = mock(Member.class);

            TaskAssignee assignee = mock(TaskAssignee.class, RETURNS_DEEP_STUBS);

            given(assignee.getProjectMember().getMember().getId()).willReturn(memberId);
            given(taskAssigneeRepository.findAllByTaskId(taskId)).willReturn(List.of(assignee));

            mockedSecurityUtil.when(SecurityUtil::getCurrentLoginId).thenReturn(loginId);
            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
            given(member.getId()).willReturn(memberId);
            given(taskRepository.findById(taskId)).willReturn(Optional.of(task));

            // when
            completedTaskService.updateCompletedTaskStatus(taskId, TaskStatus.IN_PROGRESS);

            // then
            verify(contributionService).recordContribution(eq(memberId), any());
            verify(notificationHelper, times(1)).sendToOthers(eq(task), eq(NotificationCategory.TASK_RESTARTED));
        }
    }
    @Test
    @DisplayName("완료 업무 상세 수정 시 잔디 기록 및 수정 알림 발송 검증")
    void updateCompletedTask_Success() {
        try (MockedStatic<SecurityUtil> mockedSecurityUtil = mockStatic(SecurityUtil.class)) {
            // given
            Long taskId = 1L;
            Long memberId = 10L;
            Long projectId = 100L;
            String loginId = "testUser";

            CompletedTaskUpdateReq req = new CompletedTaskUpdateReq(
                    "와이어프레임 제작 (수정)",
                    "DONE",
                    List.of(memberId),
                    "2025.11.13",
                    "2025.11.25",
                    "수정된 내용입니다.",
                    "API 속도 이슈 해결함."
            );

            Project mockProject = mock(Project.class);
            given(mockProject.getId()).willReturn(projectId);

            Task task = spy(Task.builder()
                    .id(taskId)
                    .project(mockProject)
                    .status(TaskStatus.DONE)
                    .build());
            Member member = mock(Member.class);
            given(member.getId()).willReturn(memberId);

            TaskAssignee myAssignee = mock(TaskAssignee.class, RETURNS_DEEP_STUBS);

            given(myAssignee.getProjectMember().getMember().getId()).willReturn(memberId);
            given(taskAssigneeRepository.findAllByTaskId(taskId))
                    .willReturn(List.of(myAssignee));

            mockedSecurityUtil.when(SecurityUtil::getCurrentLoginId).thenReturn(loginId);
            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
            given(taskRepository.findById(taskId)).willReturn(Optional.of(task));

            ProjectMember mockProjectMember = mock(ProjectMember.class);
            given(projectMemberRepository.findByProject_IdAndMember_Id(projectId, memberId))
                    .willReturn(Optional.of(mockProjectMember));

            TaskNote mockNote = mock(TaskNote.class);
            given(taskNoteRepository.findByTaskIdAndTaskAssignee_ProjectMember_Id(taskId, memberId))
                    .willReturn(Optional.of(mockNote));

            // when
            completedTaskService.updateCompletedTask(taskId, req);

            // then
            verify(task).updateInfo(eq(req.title()), eq(req.contents()), any(Instant.class), any(Instant.class));

            verify(task).updateStatus(TaskStatus.DONE);
            verify(taskAssigneeRepository, times(1)).deleteAllByTask(task);
            verify(taskAssigneeRepository, times(1)).saveAll(anyList());
            verify(mockNote).updateContent(req.noteContent());
            verify(contributionService).recordContribution(eq(memberId), any());
            verify(notificationHelper, times(1)).sendToOthers(eq(task), eq(NotificationCategory.TASK_MODIFIED));
        }
    }

    @Test
    @DisplayName("완료 업무 상세 조회 시 내 회고 내용이 포함되어야 한다")
    void getCompletedTaskDetail_Success() {
        // given
        Long taskId = 1L;
        Long memberId = 1L;
        String loginId = "testUser";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Task task = Task.builder()
                .id(taskId)
                .name("테스트 업무")
                .content("내용")
                .status(TaskStatus.DONE)
                .startDate(java.time.Instant.now())
                .dueDate(java.time.Instant.now())
                .build();

        TaskNote note = TaskNote.builder().content("나의 회고록").build();

        Member mockMember = mock(Member.class);
        given(mockMember.getId()).willReturn(memberId);

        given(memberRepository.findByLoginId(any())).willReturn(Optional.of(mockMember));
        given(taskRepository.findById(taskId)).willReturn(Optional.of(task));
        given(taskNoteRepository.findByTaskIdAndTaskAssignee_ProjectMember_Id(taskId, memberId))
                .willReturn(Optional.of(note));

        // when
        CompletedTaskDetailRes result = completedTaskService.getCompletedTaskDetail(taskId);

        // then
        assertThat(result.noteContent()).isEqualTo("나의 회고록");
        verify(taskNoteRepository, times(1)).findByTaskIdAndTaskAssignee_ProjectMember_Id(taskId, memberId);
        verify(contributionService, never()).recordContribution(any(), any());
    }

    @Test
    @DisplayName("업무 삭제 호출 시 실제로 삭제되지 않고 deletedAt 필드만 채워져야 한다 (Soft Delete)")
    void deleteCompletedTask_SoftDelete() {
        // given
        Long taskId = 1L;
        Task task = spy(Task.builder()
                .id(taskId)
                .status(TaskStatus.DONE)
                .build());

        given(taskRepository.findById(taskId)).willReturn(Optional.of(task));

        // when
        completedTaskService.deleteCompletedTask(taskId);

        // then
        verify(task).softDelete();
        assertThat(task.getDeletedAt()).isNotNull();
    }
}
