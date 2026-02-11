package com.connecteamed.server.domain.notification.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.entity.NotificationType;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.notification.repository.NotificationTypeRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
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
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    @Async("AsyncExecutor")
    @Transactional
    public void send(Long receiverId, Long senderId, Long projectId, Long taskId, String typeKey) {
        try {
            Member receiver = memberRepository.findById(receiverId)
                    .orElseThrow(() -> {
                        log.error("[알림 실패] 존재하지 않는 회원 ID: {}", receiverId);
                        return new GeneralException(GeneralErrorCode.NOT_FOUND);
                    });

            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> {
                        log.error("[알림 실패] 존재하지 않는 프로젝트 ID: {}", projectId);
                        return new GeneralException(GeneralErrorCode.NOT_FOUND);
                    });

            NotificationCategory category = NotificationCategory.from(typeKey);

            Notification notification = Notification.builder()
                    .receiver(receiver)
                    .project(project)
                    .category(category)
                    .content(category.getMessage())
                    .targetUrl(category.generateUrl(project.getId(), taskId))
                    .isRead(false)
                    .build();

            notificationRepository.save(notification);

        } catch (GeneralException e) {
            log.error("알림 발송 비즈니스 예외 발생: {}", e.getCode());
        } catch (Exception e) {
            log.error("알림 발송 시스템 오류: ", e);
        }
    }
}