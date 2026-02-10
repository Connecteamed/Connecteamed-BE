package com.connecteamed.server.domain.task.service;

import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.service.NotificationCommandService;
import com.connecteamed.server.domain.notification.service.NotificationHelper;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.task.dto.CompletedTaskDetailRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskListRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskUpdateReq;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.entity.TaskAssignee;
import com.connecteamed.server.domain.task.entity.TaskNote;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.exception.TaskErrorCode;
import com.connecteamed.server.domain.task.exception.TaskException;
import com.connecteamed.server.domain.task.repository.TaskAssigneeRepository;
import com.connecteamed.server.domain.task.repository.TaskNoteRepository;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletedTaskService {

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskNoteRepository taskNoteRepository;
    private final MemberRepository  memberRepository;
    private final NotificationCommandService  notificationCommandService;
    private final ContributionService contributionService;
    private final NotificationHelper notificationHelper;
    private final ProjectMemberRepository projectMemberRepository;

    // 완료한 업무 목록 조회
    public CompletedTaskListRes getCompletedTasks(Long projectId) {
        List<Task> completedTasks = taskRepository.findAllByProjectIdAndStatusAndDeletedAtIsNull(
                projectId,
                TaskStatus.DONE
        );

        if (completedTasks.isEmpty()) {
            return new CompletedTaskListRes(Collections.emptyList());
        }

        List<Long> taskIds = completedTasks.stream()
                .map(Task::getId)
                .toList();

        List<TaskAssignee> allAssignees = taskAssigneeRepository.findAllByTaskIdIn(taskIds);

        Map<Long, List<String>> assigneeMap = allAssignees.stream()
                .collect(Collectors.groupingBy(
                        assignee -> assignee.getTask().getId(),
                        Collectors.mapping(
                                assignee -> assignee.getProjectMember().getMember().getName(),
                                Collectors.toList()
                        )
                ));

        List<CompletedTaskListRes.TaskSummary> summaries = completedTasks.stream()
                .map(task -> new CompletedTaskListRes.TaskSummary(
                        task.getId(),
                        task.getName(),
                        task.getContent(),
                        task.getStartDate(),
                        task.getDueDate(),
                        task.getStatus().name(),
                        assigneeMap.getOrDefault(task.getId(), Collections.emptyList())
                )).toList();

        return new CompletedTaskListRes(summaries);
    }

    // 완료한 업무 상태 변경
    @Transactional
    public void updateCompletedTaskStatus(Long taskId, TaskStatus taskStatus) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 ID의 업무를 찾을 수 없습니다."));

        Long currentMemberId = getCurrentUserId();

        boolean isAssignee = taskAssigneeRepository.findAllByTaskId(taskId).stream()
                .anyMatch(a -> a.getProjectMember().getMember().getId().equals(currentMemberId));

        if (!isAssignee) {
            throw new TaskException(TaskErrorCode.TASK_ACCESS_FORBIDDEN, "해당 업무의 담당자가 아니므로 상태를 변경할 수 없습니다.");
        }

        TaskStatus oldStatus = task.getStatus();
        task.updateStatus(taskStatus);

        contributionService.recordContribution(currentMemberId,
                new ContributionReq(ContributionAction.COMPLETED_TASK_UPDATE, taskId));

        // 완료한 업무 상태 변경 시 알림 발송
        if (oldStatus == TaskStatus.DONE && taskStatus == TaskStatus.IN_PROGRESS) {
            notificationHelper.sendToOthers(task, NotificationCategory.TASK_RESTARTED);
        }
    }

    // 완료한 업무 상세 조회
    public CompletedTaskDetailRes getCompletedTaskDetail(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 ID의 업무를 찾을 수 없습니다."));

        List<Long> assigneeIds = getAssigneeIds(taskId);

        Long currentMemberId = getCurrentUserId();

        String myNote = taskNoteRepository.findByTaskIdAndTaskAssignee_ProjectMember_Id(taskId, currentMemberId)
                .map(TaskNote::getContent)
                .orElse("");

        return new CompletedTaskDetailRes(
                task.getId(),
                task.getName(),
                task.getStatus().name(),
                assigneeIds,
                task.getStartDate(),
                task.getDueDate(),
                task.getContent(),
                myNote
        );
    }

    // 완료한 업무 상세 수정
    @Transactional
    public void updateCompletedTask(Long taskId, CompletedTaskUpdateReq req) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND, "해당 ID의 업무를 찾을 수 없습니다."));

        Long currentMemberId = getCurrentUserId();

        taskAssigneeRepository.findAllByTaskId(taskId).stream()
                .filter(a -> a.getProjectMember().getMember().getId().equals(currentMemberId))
                .findFirst()
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_ACCESS_FORBIDDEN, "해당 업무의 담당자가 아니므로 수정할 수 없습니다."));

        Instant start = Instant.parse(req.startDate().replace(".", "-") + "T00:00:00Z");
        Instant end = Instant.parse(req.endDate().replace(".", "-") + "T23:59:59Z");

        task.updateInfo(req.title(), req.contents(), start, end);
        task.updateStatus(TaskStatus.valueOf(req.status()));

        taskAssigneeRepository.deleteAllByTask(task);
        List<TaskAssignee> newAssignees = req.assigneeIds().stream()
                .map(memberId -> {
                    var projectMember = projectMemberRepository.findByProject_IdAndMember_Id(task.getProject().getId(), memberId)
                            .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "프로젝트에 속하지 않은 멤버(ID: " + memberId + ")를 담당자로 지정할 수 없습니다."));

                    return TaskAssignee.builder()
                            .task(task)
                            .projectMember(projectMember)
                            .build();
                }).toList();

        taskAssigneeRepository.saveAll(newAssignees);

        TaskNote note = taskNoteRepository.findByTaskIdAndTaskAssignee_ProjectMember_Id(taskId, currentMemberId)
                .orElseGet(() -> createNewNote(task, currentMemberId));
        note.updateContent(req.noteContent());

        contributionService.recordContribution(currentMemberId,
                new ContributionReq(ContributionAction.COMPLETED_TASK_UPDATE, taskId));

        // 완료한 업무 정보 수정 시 알림 발송
        notificationHelper.sendToOthers(task, NotificationCategory.TASK_MODIFIED);

    }

    // 완료한 업무 삭제
    @Transactional
    public void deleteCompletedTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 ID의 업무를 찾을 수 없습니다."));
        task.softDelete();
    }

    private List<Long> getAssigneeIds(Long taskId) {
        return taskAssigneeRepository.findAllByTaskId(taskId).stream()
                .map(a -> a.getProjectMember().getMember().getId())
                .toList();
    }

    private TaskNote createNewNote(Task task, Long memberId) {
        TaskAssignee assignee = taskAssigneeRepository.findAllByTaskId(task.getId()).stream()
                .filter(a -> a.getProjectMember().getMember().getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 업무의 담당자가 아니므로 노트를 작성할 수 없습니다."));

        return taskNoteRepository.save(TaskNote.builder()
                .task(task)
                .taskAssignee(assignee)
                .content("")
                .build());
    }

    private Long getCurrentUserId() {
        String loginId = SecurityUtil.getCurrentLoginId();
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED, "인증된 사용자 정보를 찾을 수 없습니다."))
                .getId();
    }
}
