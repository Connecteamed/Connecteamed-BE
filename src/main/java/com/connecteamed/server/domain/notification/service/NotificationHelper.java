package com.connecteamed.server.domain.notification.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.entity.TaskAssignee;
import com.connecteamed.server.domain.task.repository.TaskAssigneeRepository;
import com.connecteamed.server.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationHelper {
    private final NotificationCommandService notificationCommandService;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final ProjectMemberRepository projectMemberRepository;

    /**
     * 모든 담당자에게 알림 발송
     */
    public void sendToAllAssignees(Task task, NotificationCategory category) {
        List<TaskAssignee> assignees = taskAssigneeRepository.findAllByTask(task);
        for (TaskAssignee ta : assignees) {
            send(ta, task, category);
        }
    }

    /**
     * 현재 사용자를 제외한 담당자들에게 알림 발송
     */
    public void sendToOthers(Task task, NotificationCategory category) {
        String currentLoginId = SecurityUtil.getCurrentLoginId();
        List<TaskAssignee> assignees = taskAssigneeRepository.findAllByTask(task);

        for (TaskAssignee ta : assignees) {
            Member receiver = ta.getProjectMember().getMember();
            if (receiver != null && !receiver.getLoginId().equals(currentLoginId)) {
                send(ta, task, category);
            }
        }
    }

    private void send(TaskAssignee ta, Task task, NotificationCategory category) {
        Member receiver = ta.getProjectMember().getMember();
        if (receiver != null) {
            notificationCommandService.send(receiver, null, task.getProject(), task.getId(), category.name());
        }
    }

    /**
     * 프로젝트의 모든 멤버에게 알림 발송 (프로젝트 종료 시 사용)
     */
    public void sendToAllProjectMembers(Project project, NotificationCategory category) {
        List<ProjectMember> members = projectMemberRepository.findAllByProjectId(project.getId());

        for (ProjectMember pm : members) {
            notificationCommandService.send(
                    pm.getMember(),
                    null,
                    project,
                    null,
                    category.name()
            );
        }
    }
}
