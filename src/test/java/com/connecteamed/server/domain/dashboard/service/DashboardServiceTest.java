package com.connecteamed.server.domain.dashboard.service;

import com.connecteamed.server.domain.dashboard.dto.DailyScheduleListRes;
import com.connecteamed.server.domain.dashboard.dto.DashboardRes;
import com.connecteamed.server.domain.dashboard.dto.NotificationListRes;
import com.connecteamed.server.domain.dashboard.dto.UpcomingTaskListRes;
import com.connecteamed.server.domain.meeting.entity.Meeting;
import com.connecteamed.server.domain.meeting.repository.MeetingRepository;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.retrospective.entity.AiRetrospective;
import com.connecteamed.server.domain.retrospective.repository.RetrospectiveRepository;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {

    @InjectMocks
    private DashboardService dashboardService;

    @Mock
    private RetrospectiveRepository retrospectiveRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MeetingRepository meetingRepository;

    private Project testProject;
    private final String userId = "testUser";

    @BeforeEach
    void setUp(){
        testProject = Project.builder().name("테스트 프로젝트").build();
    }

    @Test
    @DisplayName("1. 최근 회고 목록 조회 - 엔티티가 DTO로 정확히 변환된다.")
    void getRecentRetrospectives_Success() {
        AiRetrospective retro = AiRetrospective.builder()
                .id(1L).title("회고 제목").project(testProject).build();

        org.springframework.test.util.ReflectionTestUtils.setField(retro, "createdAt", java.time.Instant.now());
        given(retrospectiveRepository.findRecentRetrospectives()).willReturn(List.of(retro));

        DashboardRes.RetrospectiveListRes result = dashboardService.getRecentRetrospectives();

        assertThat(result.getRetrospectives()).hasSize(1);
        assertThat(result.getRetrospectives().get(0).getTitle()).isEqualTo("회고 제목");
        assertThat(result.getRetrospectives().get(0).getWrittenDate()).isNotNull();
    }

    @Test
    @DisplayName("2. 마감 임박 업무 조회 - name 필드와 Instant 타입이 정확히 매핑된다")
    void getUpcomingTasks_Success() {
        Task task = Task.builder()
                .id(1L).name("마감 업무").status(TaskStatus.TODO).project(testProject)
                .dueDate(Instant.now()).build();
        given(taskRepository.findUpcomingTasksByUserId(anyString(), anyList())).willReturn(List.of(task));

        UpcomingTaskListRes result = dashboardService.getUpcomingTasks(userId);

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().get(0).title()).isEqualTo("마감 업무");
    }

    @Test
    @DisplayName("3. 알림 목록 조회 - 수신자의 모든 알림을 가져온다")
    void getRecentNotifications_Success() {
        Notification notification = Notification.builder()
                .id(1L).content("새로운 메시지").project(testProject).isRead(false).build();

        Page<Notification> notificationPage = new PageImpl<>(List.of(notification));
        given(notificationRepository.findAllByReceiverLoginIdOrderByCreatedAtDesc(anyString(), any(Pageable.class))).willReturn(notificationPage);

        NotificationListRes result = dashboardService.getRecentNotifications(userId);

        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).message()).isEqualTo("새로운 메시지");
    }

    @Test
    @DisplayName("4. 날짜별 일정 조회 - 업무와 회의가 통합되고 시간순 정렬된다")
    void getDailySchedules_Success() {
        Instant now = Instant.now();
        Task task = Task.builder()
                .id(1L).name("오후 업무").project(testProject).dueDate(now.plus(5, ChronoUnit.HOURS)).build();
        Meeting meeting = Meeting.builder()
                .id(1L).title("오전 회의").project(testProject)
                .meetingDate(now.plus(1, ChronoUnit.HOURS)).build();

        given(taskRepository.findAllByMemberAndDate(anyString(), any(), any())).willReturn(List.of(task));
        given(meetingRepository.findAllByMemberAndDate(anyString(), any(), any())).willReturn(List.of(meeting));

        DailyScheduleListRes result = dashboardService.getDailySchedules(userId, now);

        assertThat(result.schedules()).hasSize(2);
        assertThat(result.schedules().get(0).title()).contains("[회의]");
        assertThat(result.schedules().get(1).title()).contains("[업무]");
    }
}
