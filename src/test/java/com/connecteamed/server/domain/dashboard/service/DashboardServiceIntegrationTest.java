package com.connecteamed.server.domain.dashboard.service;

import com.connecteamed.server.domain.dashboard.dto.DailyScheduleListRes;
import com.connecteamed.server.domain.dashboard.dto.NotificationListRes;
import com.connecteamed.server.domain.dashboard.dto.UpcomingTaskListRes;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.enums.SocialType;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.meeting.entity.Meeting;
import com.connecteamed.server.domain.meeting.repository.MeetingRepository;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.entity.NotificationType;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.entity.TaskAssignee;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class DashboardServiceIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager em;

    private Member testMember;
    private Project testProject;
    private ProjectMember testProjectMember;

    @BeforeEach
    void setUp() {
        // 테스트용 기본 데이터 저장
        testMember = memberRepository.save(Member.builder()
                .loginId("testUser")
                .name("테스터")
                .socialType(SocialType.LOCAL)
                .build());

        testProject = projectRepository.save(Project.builder()
                .name("테스트 프로젝트")
                .owner(testMember)
                .goal("테스트 프로젝트 목표")
                .build());

        testProjectMember = projectMemberRepository.save(ProjectMember.builder()
                .project(testProject)
                .member(testMember)
                .build());
    }

    @Test
    @DisplayName("통합 테스트 1: 마감 임박 업무 조회 (TaskAssignee 연관관계 쿼리 검증)")
    void getUpcomingTasks_Integration() {
        // Given: 업무 생성 및 담당자 지정
        Task task = taskRepository.save(Task.builder()
                .project(testProject)
                .name("마감 업무")
                .content("내용")
                .status(TaskStatus.TODO)
                .startDate(Instant.now())
                .dueDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());

        // TaskAssignee를 통해 연관관계 맺기
        task.getAssignees().add(TaskAssignee.builder()
                .task(task)
                .projectMember(testProjectMember)
                .build());
        taskRepository.save(task);

        // When: 서비스 호출 (JPQL 실행)
        UpcomingTaskListRes result = dashboardService.getUpcomingTasks(testMember.getLoginId());

        // Then: 쿼리가 정상 작동하여 데이터를 가져오는지 검증
        assertThat(result.tasks()).isNotEmpty();
        assertThat(result.tasks().get(0).title()).isEqualTo("마감 업무");
    }

    @Test
    @DisplayName("통합 테스트 2: 알림 목록 조회 (메서드 이름 쿼리 검증)")
    void getRecentNotifications_Integration() {
        // Given: 알림 타입 생성 및 em을 통해 직접 저장
        NotificationType type = NotificationType.builder()
                .typeKey("TASK_TAGGED")
                .displayName("업무 태그 알림")
                .build();
        em.persist(type);

        // Notification 저장
        notificationRepository.save(Notification.builder()
                .receiver(testMember)
                .project(testProject)
                .content("새로운 알림")
                .targetUrl("/test")
                .isRead(false)
                .category(NotificationCategory.TASK_TAGGED)
                .build());

        // When: 서비스 호출
        NotificationListRes result = dashboardService.getRecentNotifications(testMember.getLoginId());

        // Then: 검증
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).message()).isEqualTo("새로운 알림");
    }

    @Test
    @DisplayName("통합 테스트 3: 날짜별 일정 조회 (Meeting/Task JOIN FETCH 쿼리 검증)")
    void getDailySchedules_Integration() {
        // Given: 오늘 날짜의 회의 저장
        Instant testTime = Instant.now().plus(2, ChronoUnit.HOURS);
        Meeting meeting = Meeting.builder()
                .project(testProject)
                .title("중요 회의")
                .meetingDate(testTime)
                .build();

        meeting.addAttendee(testProjectMember);
        meetingRepository.save(meeting);

        em.flush();
        em.clear();

        // When: 오늘 일정 조회
        DailyScheduleListRes result = dashboardService.getDailySchedules(testMember.getLoginId(), testTime);

        // Then: Meeting 연관관계(Attendees)를 통해 조회가 잘 되는지 검증
        assertThat(result.schedules()).isNotEmpty();
        assertThat(result.schedules()).anyMatch(s -> s.title().contains("중요 회의"));
    }
}