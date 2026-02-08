package com.connecteamed.server.domain.notification.entity;

import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "notification_type")
public class NotificationType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_key", nullable = false, columnDefinition = "TEXT")
    private String typeKey; // ex TASK_TAGGED, TASK_DONE_SOON

    @Column(name = "display_name", nullable = false, columnDefinition = "TEXT")
    private String displayName; // ex "업무 태그 알림"

    public String getMessage() {
        return NotificationCategory.from(this.typeKey).getMessage();
    }

    public String getTargetUrl(Long projectId, Long taskId) {
        return NotificationCategory.from(this.typeKey).generateUrl(projectId, taskId);
    }
}