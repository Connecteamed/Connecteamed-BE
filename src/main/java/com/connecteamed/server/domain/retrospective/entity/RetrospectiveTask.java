package com.connecteamed.server.domain.retrospective.entity;

import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "retrospective_task")
@SQLDelete(sql = "UPDATE retrospective_task SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RetrospectiveTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retrospective_id", nullable = false)
    private AiRetrospective aiRetrospective;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    public void setAiRetrospective(AiRetrospective aiRetrospective) {
        this.aiRetrospective = aiRetrospective;
    }
}