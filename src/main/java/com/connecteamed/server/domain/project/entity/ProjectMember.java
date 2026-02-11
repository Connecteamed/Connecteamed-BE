package com.connecteamed.server.domain.project.entity;


import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.entity.TaskAssignee;
import com.connecteamed.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Builder
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
@Getter
@Table(name= "project_member",

uniqueConstraints = {
@UniqueConstraint(
        name = "uk_project_member_project_member",
        columnNames = {"project_id", "member_id"} // 조합 unique 반영
)
        }
)
public class ProjectMember extends BaseEntity {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id",nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder.Default
    @OneToMany(mappedBy = "projectMember", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectMemberRole> roles = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "projectMember")
    private Set<TaskAssignee> taskAssignees = new LinkedHashSet<>();

    public List<Task> getTasks() {
        return this.taskAssignees.stream()
                .map(TaskAssignee::getTask)
                .toList();
    }
}
