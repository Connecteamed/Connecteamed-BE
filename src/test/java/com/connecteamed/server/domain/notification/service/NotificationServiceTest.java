package com.connecteamed.server.domain.notification.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.notification.dto.NotificationListRes;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.entity.NotificationType;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("알림 목록 조회 성공")
    void getNotifications_Success() {
        // given
        String loginId = "user123";
        Pageable pageable = PageRequest.of(0, 10);

        NotificationType type = NotificationType.builder()
                .typeKey("TASK_TAGGED")
                .displayName("업무 태그 알림")
                .build();

        Project project = Project.builder().name("테스트 프로젝트").build();
        Notification notification = Notification.builder()
                .id(1L)
                .receiver(Member.builder().loginId(loginId).build())
                .project(project)
                .notificationType(type)
                .content("새로운 업무에 태그됐어요")
                .isRead(false)
                .build();

        ReflectionTestUtils.setField(notification, "createdAt", Instant.now());

        Page<Notification> notificationPage = new PageImpl<>(List.of(notification));

        when(notificationRepository.findAllByReceiverLoginIdOrderByCreatedAtDesc(loginId, pageable))
                .thenReturn(notificationPage);
        when(notificationRepository.countUnreadByReceiverLoginId(loginId))
                .thenReturn(1L);

        // when
        NotificationListRes result = notificationService.getNotifications(loginId, pageable);

        // then
        assertThat(result.unreadCount()).isEqualTo(1);
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).title()).isEqualTo("테스트 프로젝트");
    }

    @Test
    @DisplayName("알림 삭제 성공")
    void deleteNotification_Success() {
        // given
        Long notificationId = 1L;
        String loginId = "user123";
        Member receiver = Member.builder().loginId(loginId).build();
        Notification notification = Notification.builder()
                .id(notificationId)
                .receiver(receiver)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // when
        notificationService.deleteNotification(notificationId, loginId);

        // then
        verify(notificationRepository, times(1)).delete(notification);
    }

    @Test
    @DisplayName("알림 삭제 실패 - 권한 없음")
    void deleteNotification_Fail_Unauthorized() {
        // given
        Long notificationId = 1L;
        String loginId = "user123";
        String otherId = "other456";
        Member receiver = Member.builder().loginId(otherId).build();
        Notification notification = Notification.builder()
                .id(notificationId)
                .receiver(receiver)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // when & then
        assertThatThrownBy(() -> notificationService.deleteNotification(notificationId, loginId))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining(GeneralErrorCode.UNAUTHORIZED.getMessage());
    }
}
