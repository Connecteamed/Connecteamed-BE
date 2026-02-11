package com.connecteamed.server.domain.notification.entity;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "notification_new")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false) // 알림 수신자 id
    private Member receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id") // 발신자 id (시스템 발송일 경우 NULL 허용)
    private Member sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false) //관련 프로젝트
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private NotificationCategory category;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 백엔드에서 조립된 최종 메시지

    @Column(name = "target_url", nullable = false, columnDefinition = "TEXT")
    private String targetUrl; // 클릭 시 이동 경로

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;
}
