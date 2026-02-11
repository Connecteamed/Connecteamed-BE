package com.connecteamed.server.domain.task.service;

import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.service.NotificationCommandService;
import com.connecteamed.server.domain.notification.service.NotificationHelper;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.domain.task.dto.*;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.entity.TaskAssignee;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.exception.TaskErrorCode;
import com.connecteamed.server.domain.task.exception.TaskException;
import com.connecteamed.server.domain.task.repository.TaskAssigneeRepository;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.SecurityUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    private final NotificationCommandService  notificationCommandService;
    private final ContributionService contributionService;
    private final MemberRepository memberRepository;
    private final NotificationHelper notificationHelper;

    //업무 추가
    @Override
    public Long createTask(Long projectId, TaskCreateReq req) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.PROJECT_NOT_FOUND));

        if (req.startDate().isAfter(req.dueDate())) {
            throw new TaskException(TaskErrorCode.INVALID_SCHEDULE);
        }

        Task task = Task.builder()
                .project(project)
                .name(req.name())
                .content(req.content())
                .startDate(req.startDate())
                .dueDate(req.dueDate())
                .build();

        Task saved = taskRepository.save(task);

        List<Long> assigneeIds = req.assigneeProjectMemberIds() == null ? List.of() : req.assigneeProjectMemberIds();
        attachAssignees(saved, projectId, assigneeIds);

        // 알림: 업무 태그
        notificationHelper.sendToAllAssignees(saved, NotificationCategory.TASK_TAGGED);

        contributionService.recordContribution(getCurrentUserId(), projectId,
                new ContributionReq(ContributionAction.TASK_CREATE, saved.getId()));

        return saved.getId();
    }

    //업무 목록 조회(전체)
    @Override
    public List<TaskSummaryRes> getProjectTasks(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.PROJECT_NOT_FOUND));

        List<Task> tasks = taskRepository.findAllByProject_IdAndDeletedAtIsNullOrderByStartDateAsc(projectId);
        if (tasks.isEmpty()) return List.of();

        // 1번 쿼리로 담당자/멤버까지 한 번에 로딩
        List<TaskAssignee> allAssignees = taskAssigneeRepository.findAllByTaskInWithDetails(tasks);

        // taskId(또는 task) 기준으로 grouping
        var assigneeMap = allAssignees.stream()
                .collect(Collectors.groupingBy(ta -> ta.getTask().getId()));

        List<TaskSummaryRes> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            List<TaskAssignee> tas = assigneeMap.getOrDefault(task.getId(), List.of());
            List<TaskAssigneeRes> assignees = toAssigneeRes(tas);

            result.add(new TaskSummaryRes(
                    task.getId(),
                    task.getName(),
                    task.getContent(),
                    task.getStatus(),
                    task.getStartDate(),
                    task.getDueDate(),
                    assignees
            ));
        }
        return result;
    }

    //업무 상세 조회
    @Override
    public TaskDetailRes getTaskDetail(Long taskId) {
        Task task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND));

        List<TaskAssigneeRes> assignees = toAssigneeRes(taskAssigneeRepository.findAllByTask(task));

        return new TaskDetailRes(
                task.getId(),
                task.getProject().getId(),
                task.getName(),
                task.getContent(),
                task.getStatus(),
                task.getStartDate(),
                task.getDueDate(),
                assignees,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    // 업무 상태 변경 TODO: Completed Task 겹침
    @Override
    public void updateTaskStatus(Long taskId, TaskStatusUpdateReq req) {
        Task task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND));

        Long currentMemberId = getCurrentUserId();
        validateProjectAccess(task.getProject().getId(), currentMemberId);

        TaskStatus oldStatus = task.getStatus();
        task.changeStatus(req.status());

        contributionService.recordContribution(currentMemberId, task.getProject().getId(),
                new ContributionReq(ContributionAction.TASK_UPDATE, taskId));

        // 알림: 다시 진행 중 or 완료
        if (oldStatus == TaskStatus.DONE && req.status() == TaskStatus.IN_PROGRESS) {
            notificationHelper.sendToOthers(task, NotificationCategory.TASK_RESTARTED);
        } else if (req.status() == TaskStatus.DONE) {
            notificationHelper.sendToOthers(task, NotificationCategory.TASK_COMPLETED);
        }
    }

    // 업무 일정 수정
    @Override
    public void updateTaskSchedule(Long taskId, TaskScheduleUpdateReq req) {
        Task task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND));

        validateProjectAccess(task.getProject().getId(), getCurrentUserId());

        if (req.startDate().isAfter(req.dueDate())) {
            throw new TaskException(TaskErrorCode.INVALID_SCHEDULE);
        }

        task.changeSchedule(req.startDate(), req.dueDate());

        contributionService.recordContribution(getCurrentUserId(), task.getProject().getId(),
                new ContributionReq(ContributionAction.TASK_UPDATE, taskId));

        // 알림: 업무 내용 수정
        notificationHelper.sendToOthers(task, NotificationCategory.TASK_MODIFIED);
    }

    // 업무 담당자 변경
    @Override
    public void updateTaskAssignees(Long taskId, TaskAssigneeUpdateReq req) {
        Task task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND));

        Long projectId = task.getProject().getId();
        validateProjectAccess(projectId, getCurrentUserId());

        taskAssigneeRepository.deleteAllByTask(task);
        taskAssigneeRepository.flush();

        List<Long> assigneeIds = req.assigneeProjectMemberIds() == null ? List.of() : req.assigneeProjectMemberIds();
        attachAssignees(task, projectId, assigneeIds);

        contributionService.recordContribution(getCurrentUserId(), projectId,
                new ContributionReq(ContributionAction.TASK_UPDATE, taskId));

        // 알림: 새로 태그된 사람들에게 알림 발송
        notificationHelper.sendToAllAssignees(task, NotificationCategory.TASK_TAGGED);
    }

    // 업무 삭제 TODO: Completed Task 겹침 
    @Override
    public void deleteTask(Long taskId) {
        Task task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND));

        validateProjectAccess(task.getProject().getId(), getCurrentUserId());

        task.softDelete();
    }

    private void attachAssignees(Task task, Long projectId, List<Long> projectMemberIds) {
        if (projectMemberIds == null || projectMemberIds.isEmpty()) {
            return;
        }

        List<Long> distinctIds = projectMemberIds.stream().distinct().toList();

        List<ProjectMember> members = projectMemberRepository.findAllByIdIn(distinctIds);
        if (members.size() != distinctIds.size()) {
            throw new TaskException(TaskErrorCode.INVALID_ASSIGNEE);
        }

        for (ProjectMember pm : members) {
            if (pm.getProject() == null || !Objects.equals(pm.getProject().getId(), projectId)) {
                throw new TaskException(TaskErrorCode.ASSIGNEE_NOT_IN_PROJECT);
            }
        }

        List<TaskAssignee> entities = members.stream()
                .map(pm -> TaskAssignee.builder()
                        .task(task)
                        .projectMember(pm)
                        .build()
                )
                .collect(Collectors.toList());

        taskAssigneeRepository.saveAll(entities);
    }

    private List<TaskAssigneeRes> toAssigneeRes(List<TaskAssignee> assignees) {
        List<TaskAssigneeRes> result = new ArrayList<>();
        for (TaskAssignee ta : assignees) {
            ProjectMember pm = ta.getProjectMember();

            Long projectMemberId = pm.getId();
            Long memberId = pm.getMember() != null ? pm.getMember().getId() : null;
            UUID memberPublicId = pm.getMember() != null ? pm.getMember().getPublicId() : null;
            String memberName = pm.getMember() != null ? pm.getMember().getName() : null;

            result.add(new TaskAssigneeRes(projectMemberId, memberId , memberName));
        }
        return result;
    }

    private Long getCurrentUserId() {
        String loginId = SecurityUtil.getCurrentLoginId();
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED, "인증된 사용자 정보를 찾을 수 없습니다."))
                .getId();
    }

    private void validateProjectAccess(Long projectId, Long memberId) {
        if (!projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new TaskException(TaskErrorCode.TASK_ACCESS_FORBIDDEN);
        }
    }
}
