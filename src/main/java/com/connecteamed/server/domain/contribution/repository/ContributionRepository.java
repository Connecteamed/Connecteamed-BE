package com.connecteamed.server.domain.contribution.repository;

import com.connecteamed.server.domain.contribution.entity.Contribution;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // 중복 체크
    boolean existsByUserIdAndActionTypeAndTargetId(Long userId, ContributionAction actionType, Long targetId);

    // 오늘 활동 횟수 조회
    @Query("SELECT COUNT(c) FROM Contribution c " +
            "WHERE c.userId = :userId " +
            "AND c.createdAt >= :start AND c.createdAt < :end")
    int countTodayActivities(@Param("userId") Long userId,
                             @Param("start") java.time.Instant start,
                             @Param("end") java.time.Instant end);

    // 1년치 데이터 조회
    @Query("SELECT c FROM Contribution c " +
            "WHERE c.userId = :userId " +
            "AND c.createdAt >= :start AND c.createdAt < :end")
    List<Contribution> findAllByUserIdAndRange(@Param("userId") Long userId,
                                               @Param("start") java.time.Instant start,
                                               @Param("end") java.time.Instant end);

    interface ContributionMapping {
        java.sql.Date getDate();
        Integer getCount();
    }

    // 프로젝트 멤버들의 데이터 전체 조회
    List<Contribution> findAllByUserIdInAndCreatedAtBetween(
            List<Long> userIds,
            Instant start,
            Instant end
    );
}
