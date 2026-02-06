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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationTypeRepository notificationTypeRepository;

    @Async("AsyncExecutor")
    @Transactional
    public void send(Member receiver, Member sender, Project project, Long taskId, String typeKey) {
        try {
            NotificationType notificationType = notificationTypeRepository.findByTypeKey(typeKey)
                    .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

            Notification notification = Notification.builder()
                    .receiver(receiver)
                    .sender(sender)
                    .project(project)
                    .notificationType(notificationType)
                    .content(notificationType.getMessage())
                    .targetUrl(notificationType.getTargetUrl(project.getId(), taskId))
                    .isRead(false)
                    .build();

            notificationRepository.save(notification);

    } catch (Exception e) {
        log.error("알림 발송 중 오류 발생 - 대상: {}, 타입: {}, 에러: {}",
                receiver.getId(), typeKey, e.getMessage());
        }
    }
}