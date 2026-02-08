package com.connecteamed.server.domain.notification.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.entity.NotificationType;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.project.entity.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("알림 생성 및 저장 성공 테스트")
    void send_Notification_Success() {
        // given
        Member receiver = Member.builder().id(1L).build();
        Member sender = Member.builder().id(2L).build();
        Project project = Project.builder().id(100L).name("Connected").build();
        Long taskId = 50L;
        String typeKey = "TASK_TAGGED";

        NotificationType mockType = NotificationType.builder()
                .typeKey(typeKey)
                .build();

        // when
        notificationCommandService.send(receiver, sender, project, taskId, typeKey);

        // then
        verify(notificationRepository, times(1)).save(any(Notification.class));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification savedNotification = captor.getValue();

        assertThat(savedNotification.getReceiver()).isEqualTo(receiver);
        assertThat(savedNotification.getNotificationType().getTypeKey()).isEqualTo(typeKey);
        assertThat(savedNotification.getContent()).isEqualTo("새로운 업무에 태그됐어요");
        assertThat(savedNotification.getTargetUrl()).isEqualTo("/projects/100/tasks/50");
    }
}