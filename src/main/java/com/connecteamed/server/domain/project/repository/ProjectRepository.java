package com.connecteamed.server.domain.project.repository;

import com.connecteamed.server.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByPublicId(UUID publicId);
    Optional<Project> findByName(String name);

    @Query("SELECT p FROM Project p " +
            "LEFT JOIN FETCH p.projectMembers pm " +
            "LEFT JOIN FETCH pm.taskAssignees ta " +
            "LEFT JOIN FETCH ta.task " +
            "LEFT JOIN FETCH pm.roles " +
            "WHERE p.id = :projectId")
    Optional<Project> findByIdWithDetails(@Param("projectId") Long projectId);
}
