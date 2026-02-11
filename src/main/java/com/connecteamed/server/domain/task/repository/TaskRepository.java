package com.connecteamed.server.domain.task.repository;

import com.connecteamed.server.domain.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.connecteamed.server.domain.task.enums.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndDeletedAtIsNull(Long id);

    // TODO: 업무목록 조회 & 완료된 업무 목록 조회 -> 하나로 통일 필요
    List<Task> findAllByProject_IdAndDeletedAtIsNullOrderByStartDateAsc(Long projectId);
  
    List<Task> findAllByProjectIdAndStatusAndDeletedAtIsNull(Long projectId, TaskStatus status);

    @Query("SELECT DISTINCT t FROM Task t " +
            "JOIN FETCH t.project p " +
            "JOIN t.assignees ta " +
            "JOIN ta.projectMember pm " +
            "JOIN pm.member m " +
            "WHERE m.loginId = :userId " +
            "AND t.status IN :statuses " +
            "AND t.dueDate BETWEEN :now AND :oneWeekLater " +
            "AND t.deletedAt IS NULL " +
            "ORDER BY t.dueDate ASC")
    List<Task> findUpcomingTasksByUserId(
            @Param("userId") String userId,
            @Param("statuses") List<TaskStatus> statuses,
            @Param("now") Instant now,
            @Param("oneWeekLater") Instant oneWeekLater
    );

    @Query("SELECT DISTINCT t FROM Task t " +
            "JOIN FETCH t.project p " +
            "JOIN t.assignees ta " +
            "JOIN ta.projectMember pm " +
            "JOIN pm.member m " +
            "WHERE m.loginId = :userId " +
            "AND t.dueDate >= :startOfDay AND t.dueDate < :endOfDay " +
            "AND t.deletedAt IS NULL")
    List<Task> findAllByMemberAndDate(
            @Param("userId") String userId,
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay
    );

    @Query("SELECT t FROM Task t " +
            "JOIN FETCH t.project " +
            "WHERE t.dueDate >= :start AND t.dueDate < :end " +
            "AND t.status != :status " +
            "AND t.deletedAt IS NULL")
    List<Task> findAllByDueDateBetweenAndStatusNot(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("status") TaskStatus status
    );

}
