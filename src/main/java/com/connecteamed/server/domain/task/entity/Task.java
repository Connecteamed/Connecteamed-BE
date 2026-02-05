package com.connecteamed.server.domain.task.entity;

import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Builder
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
@Getter
@Table(name= "task")
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="public_id",nullable = false,unique = true,columnDefinition = "UUID")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="project_id",nullable = false)
    private Project project;

    @Builder.Default
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskAssignee> assignees = new ArrayList<>();

    @Column(name="name",nullable = false)
    private String name;

    @Column(name="content",nullable = false,columnDefinition = "TEXT")
    private String content;

    @Column(name="result", columnDefinition = "TEXT")
    private String result;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    @Builder.Default
    private TaskStatus status=TaskStatus.TODO;

    @Column(name="start_date",nullable = false)
    private Instant startDate;

    @Column(name="due_date",nullable = false)
    private Instant dueDate;

    @Column(name="deleted_at")
    private Instant deletedAt;

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }

    public void updateInfo(String name, String content, String result) {
        this.name = name;
        this.content = content;
        this.result = result;
    }

    @PrePersist
    public void prePersist(){
        if(this.publicId == null){
            this.publicId = UUID.randomUUID();
        }
    }

    public void changeStatus(TaskStatus status) {
        this.status = status;
    }

    public void changeSchedule(Instant startDate, Instant dueDate) {
        this.startDate = startDate;
        this.dueDate = dueDate;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

}
