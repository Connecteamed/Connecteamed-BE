package com.connecteamed.server.domain.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum NotificationCategory {
    TASK_TAGGED("새로운 업무에 태그됐어요", "/projects/%d/tasks/%d"),
    TASK_RESTARTED("공동 업무가 다시 진행 중으로 변경됐어요", "/projects/%d/tasks/%d"),
    TASK_COMPLETED("공동 업무가 완료됐어요. 느낀점을 채워주세요!", "/projects/%d/completed-tasks/%d"),
    TASK_DEADLINE_APPROACHING("업무 마감이 하루 남았어요!", "/projects/%d/tasks/%d"),
    TASK_MODIFIED("담당 업무 내용이 수정됐어요", "/projects/%d/tasks/%d"),
    PROJECT_COMPLETED("프로젝트가 종료됐어요. 회고를 작성해주세요!", "/projects/%d/retrospective"),
    DEFAULT("새로운 알림이 도착했어요", "/projects/%d");

    private final String message;
    private final String urlTemplate;

    public static NotificationCategory from(String typeKey) {
        return Arrays.stream(NotificationCategory.values())
                .filter(c -> c.name().equals(typeKey))
                .findFirst()
                .orElse(DEFAULT);
    }
    public String generateUrl(Long projectId, Long taskId) {
        if (this == PROJECT_COMPLETED || this == DEFAULT) {
            return String.format(urlTemplate, projectId);
        }
        return String.format(urlTemplate, projectId, taskId);
    }
}
