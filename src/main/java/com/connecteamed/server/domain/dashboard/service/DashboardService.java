package com.connecteamed.server.domain.dashboard.service;

import com.connecteamed.server.domain.dashboard.dto.DailyScheduleListRes;
import com.connecteamed.server.domain.dashboard.dto.DashboardRes;
import com.connecteamed.server.domain.dashboard.dto.NotificationListRes;
import com.connecteamed.server.domain.dashboard.dto.UpcomingTaskListRes;
import com.connecteamed.server.domain.meeting.entity.Meeting;
import com.connecteamed.server.domain.meeting.repository.MeetingRepository;
import com.connecteamed.server.domain.notification.dto.NotificationRes;
import com.connecteamed.server.domain.notification.entity.Notification;
import com.connecteamed.server.domain.notification.repository.NotificationRepository;
import com.connecteamed.server.domain.retrospective.entity.AiRetrospective;
import com.connecteamed.server.domain.retrospective.repository.RetrospectiveRepository;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final RetrospectiveRepository retrospectiveRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final MeetingRepository meetingRepository;

    /**
     * 최근 회고 목록 조회
     * @return 회고 목록 응답 DTO (모든 회고)
     */
    public DashboardRes.RetrospectiveListRes getRecentRetrospectives() {
        List<AiRetrospective> retrospectives = retrospectiveRepository.findRecentRetrospectives();
        return convertToResponse(retrospectives);
    }

    /**
     * 로그인 사용자가 작성한 최근 회고 목록 조회
     * @param username 로그인한 사용자의 로그인 아이디
     * @return 사용자가 작성한 회고 목록 응답 DTO
     */
    public DashboardRes.RetrospectiveListRes getRecentRetrospectives(String username) {
        List<AiRetrospective> retrospectives = retrospectiveRepository.findRecentRetrospectivesByUsername(username);
        return convertToResponse(retrospectives);
    }

    public UpcomingTaskListRes getUpcomingTasks(String userId) {
        List<TaskStatus> targetStatuses = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
        List<Task> tasks = taskRepository.findUpcomingTasksByUserId(userId, targetStatuses);
        List<UpcomingTaskListRes.UpcomingTaskRes> taskResList = tasks.stream()
                .map(task -> new UpcomingTaskListRes.UpcomingTaskRes(
                        task.getId(),
                        task.getName(),
                        task.getProject().getName(),
                        task.getDueDate()
                ))
                .toList();

        return new UpcomingTaskListRes(taskResList);
    }

    public NotificationListRes getRecentNotifications(String userId) {
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Notification> notifications = notificationRepository.findAllByReceiverLoginIdOrderByCreatedAtDesc(userId, pageable).getContent();
        List<NotificationListRes.NotificationRes> resList = notifications.stream()
                .map(n -> new NotificationListRes.NotificationRes(
                        n.getId(),
                        n.getContent(),
                        n.getProject().getName(),
                        n.isRead(),
                        n.getCreatedAt()
                ))
                .toList();

        return new NotificationListRes(resList);
    }

    public DailyScheduleListRes getDailySchedules(String userId, Instant date) {
        // 1. 선택된 날짜의 00:00:00 ~ 23:59:59 범위 계산
        java.time.LocalDate localDate = date.atZone(ZoneId.systemDefault()).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = startOfDay.plus(java.time.Duration.ofDays(1)).minusNanos(1);

        // 2. Task(업무)와 Meeting(회의) 각각 조회
        List<Task> dailyTasks = taskRepository.findAllByMemberAndDate(userId, startOfDay, endOfDay);
        List<Meeting> dailyMeetings = meetingRepository.findAllByMemberAndDate(userId, startOfDay, endOfDay);

        // 3. 통합 리스트 생성 및 변환
        List<DailyScheduleListRes.ScheduleRes> resList = new java.util.ArrayList<>();

        // 업무 추가
        dailyTasks.forEach(t -> resList.add(new DailyScheduleListRes.ScheduleRes(
                t.getId(),
                "[업무] " + t.getName(),
                t.getProject().getName(),
                t.getDueDate()
        )));

        // 회의 추가
        dailyMeetings.forEach(m -> resList.add(new DailyScheduleListRes.ScheduleRes(
                m.getId(),
                "[회의] " + m.getTitle(),
                m.getProject().getName(),
                m.getMeetingDate()
        )));

        // 4. 시간순 정렬
        resList.sort(java.util.Comparator.comparing(DailyScheduleListRes.ScheduleRes::time));

        return new DailyScheduleListRes(date, resList);
    }

    /**
     * AiRetrospective 리스트를 Response DTO로 변환
     * @param retrospectives DB에서 조회한 회고 엔티티 리스트
     * @return 변환된 회고 목록 응답 DTO
     */
    private DashboardRes.RetrospectiveListRes convertToResponse(List<AiRetrospective> retrospectives) {
        List<DashboardRes.RetrospectiveInfo> retrospectiveInfos = retrospectives.stream()
                .map(retrospective -> DashboardRes.RetrospectiveInfo.builder()
                        .id(retrospective.getId())
                        .title(retrospective.getTitle())
                        .teamName(retrospective.getProject().getName())
                        .writtenDate(retrospective.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                        .build())
                .collect(Collectors.toList());

        return DashboardRes.RetrospectiveListRes.builder()
                .retrospectives(retrospectiveInfos)
                .build();
    }
}
