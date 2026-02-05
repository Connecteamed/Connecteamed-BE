package com.connecteamed.server.domain.notification.service;

import com.connecteamed.server.domain.notification.dto.NotificationListRes;
import com.connecteamed.server.domain.notification.dto.NotificationRes;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 알림 목록 조회
    public NotificationListRes getNotifications(String loginId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findAllByReceiverLoginIdOrderByCreatedAtDesc(loginId, pageable);

        long unreadCount = notificationRepository.countUnreadByReceiverLoginId(loginId);

        List<NotificationRes> responses = notifications.getContent().stream()
                .map(this::convertToResponse)
                .toList();

        return new NotificationListRes(unreadCount, responses);
    }

    // 알림 삭제
    @Transactional
    public void deleteNotification(Long notificationId, String loginId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        if (!notification.getReceiver().getLoginId().equals(loginId)) {
            throw new GeneralException(GeneralErrorCode.UNAUTHORIZED);
        }

        notificationRepository.delete(notification);
    }

    private NotificationRes convertToResponse(Notification notification) {
        return NotificationRes.builder()
                .id(notification.getId())
                .notificationType(notification.getNotificationType().getTypeKey())
                .title(notification.getProject().getName()) // 프로젝트 명을 타이틀로 사용
                .content(notification.getContent())
                .createdAt(calculateTimeAgo(notification.getCreatedAt())) // 시간 계산 호출
                .isRead(notification.isRead())
                .targetUrl(notification.getTargetUrl())
                .build();
    }

    // 시간 계산 로직
    private String calculateTimeAgo(Instant createdAt) {
        Instant now = Instant.now();
        Duration duration = Duration.between(createdAt, now);

        long seconds = duration.getSeconds();

        if (seconds < 60) return seconds + "초 전";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "분 전";
        long hours = minutes / 60;
        if (hours < 24) return hours + "시간 전";
        long days = hours / 24;
        return days + "일 전";
    }
}