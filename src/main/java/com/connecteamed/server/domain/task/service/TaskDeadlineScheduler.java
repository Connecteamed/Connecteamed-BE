package com.connecteamed.server.domain.task.service;

import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.service.NotificationHelper;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDeadlineScheduler {

    private final TaskRepository taskRepository;
    private final NotificationHelper notificationHelper;

    // 매일 오전 9시에 마감 기한이 하루 남은 업무를 찾아 알림 발송
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void notifyUpcomingDeadlines() {
        Instant now = Instant.now();
        Instant tomorrowStart = now.plus(Duration.ofDays(1)).minus(Duration.ofHours(1)); // 약 23시간 후
        Instant tomorrowEnd = now.plus(Duration.ofDays(1)).plus(Duration.ofHours(1));   // 약 25시간 후

        // 마감이 내일 범위 내에 있고, 완료되지 않은 업무 조회
        List<Task> tasks = taskRepository.findAllByDueDateBetweenAndStatusNot(
                tomorrowStart,
                tomorrowEnd,
                TaskStatus.DONE
        );

        for (Task task : tasks) {
            try {
                notificationHelper.sendToAllAssignees(task, NotificationCategory.TASK_DEADLINE_APPROACHING);
            } catch (Exception e) {
                log.error("마감 임박 알림 발송 실패 - 업무 ID: {}, 에러: {}", task.getId(), e.getMessage());
            }
        }
    }
}