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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("알림 생성 및 저장 성공 테스트")
    void send_Notification_Success() {
        // given
        Long receiverId = 1L;
        Long senderId = 2L;
        Long projectId = 100L;
        Long taskId = 50L;
        NotificationCategory category = NotificationCategory.TASK_TAGGED;

        Member receiver = Member.builder().id(receiverId).build();
        Project project = Project.builder().id(projectId).name("Connected").build();

        when(memberRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // when
        notificationCommandService.send(receiverId, senderId, projectId, taskId, category.name());

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification savedNotification = captor.getValue();

        assertThat(savedNotification.getReceiver()).isEqualTo(receiver);
        assertThat(savedNotification.getProject()).isEqualTo(project);
        assertThat(savedNotification.getCategory()).isEqualTo(category);
        assertThat(savedNotification.getContent()).isEqualTo(category.getMessage());
        assertThat(savedNotification.getTargetUrl()).isEqualTo(category.generateUrl(projectId, taskId));
    }
}