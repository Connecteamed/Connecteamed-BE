package com.connecteamed.server.domain.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum NotificationCategory {
    TASK_TAGGED("새로운 업무에 태그되었어요", "/projects/%d/tasks/%d"),
    TASK_RESTARTED("내 업무의 상태가 변경됐어요", "/projects/%d/tasks/%d"),
    TASK_COMPLETED("내 업무의 상태가 변경됐어요", "/projects/%d/completed-tasks/%d"),
    TASK_NOTE_REQUIRED("업무 느낀점을 작성해주세요. 느낀 점을 작성해야 정확한 회고가 가능해요", "/projects/%d/tasks/%d"),
    TASK_DEADLINE_APPROACHING("곧 마감이 다가와요", "/projects/%d/tasks/%d"),
    TASK_MODIFIED("내 업무가 수정됐어요", "/projects/%d/tasks/%d"),
    PROJECT_COMPLETED("완료된 프로젝트를 회고해보세요", "/projects/%d/retrospective"),
    RETROSPECTIVE_COMPLETED("요청하신 회고가 완료됐어요. 지금 확인해보세요!", "/projects/%d/retrospective/%d"),
    DEFAULT("새로운 알림이 도착했어요", "/projects/%d");

    private final String message;
    private final String urlTemplate;

    public static NotificationCategory from(String typeKey) {
        return Arrays.stream(NotificationCategory.values())
                .filter(c -> c.name().equals(typeKey))
                .findFirst()
                .orElse(DEFAULT);
    }
    public String generateUrl(Long projectId, Long targetId) {
        if (this == PROJECT_COMPLETED || this == DEFAULT || targetId == null) {
            return String.format(urlTemplate, projectId);
        }
        return String.format(urlTemplate, projectId, targetId);
    }
}
