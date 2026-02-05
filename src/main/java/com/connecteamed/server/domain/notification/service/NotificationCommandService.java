package com.connecteamed.server.domain.notification.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.entity.NotificationType;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.notification.repository.NotificationTypeRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationTypeRepository notificationTypeRepository;

    @Async("AsyncExecutor")
    @Transactional
    public void send(Member receiver, Member sender, Project project, Long taskId, String typeKey) {

        NotificationType notificationType = notificationTypeRepository.findByTypeKey(typeKey)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .project(project)
                .notificationType(notificationType)
                .content(generateContent(typeKey))
                .targetUrl(generateTargetUrl(project.getId(), taskId, typeKey))
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    private String generateTargetUrl(Long projectId, Long taskId, String typeKey) {
        return switch (typeKey) {
            case "TASK_COMPLETED"
                    -> String.format("/projects/%d/completed-tasks/%d", projectId, taskId);
            case "TASK_TAGGED", "TASK_RESTARTED", "TASK_DEADLINE_APPROACHING", "TASK_MODIFIED"
                    -> String.format("/projects/%d/tasks/%d", projectId, taskId);
            case "PROJECT_COMPLETED"
                    -> String.format("/projects/%d/retrospective", projectId);
            default -> String.format("/projects/%d", projectId);
        };
    }

    private String generateContent(String typeKey) {
        return switch (typeKey) {
            case "TASK_TAGGED" -> "새로운 업무에 태그됐어요";
            case "TASK_RESTARTED" -> "공동 업무가 다시 진행 중으로 변경됐어요";
            case "TASK_COMPLETED" -> "공동 업무가 완료됐어요. 느낀점을 채워주세요!";
            case "TASK_DEADLINE_APPROACHING" -> "업무 마감이 하루 남았어요!";
            case "TASK_MODIFIED" -> "담당 업무 내용이 수정됐어요";
            case "PROJECT_COMPLETED" -> "프로젝트가 종료됐어요. 회고를 작성해주세요!";
            default -> "새로운 알림이 도착했어요";
        };
    }
}