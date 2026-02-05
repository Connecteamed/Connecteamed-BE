package com.connecteamed.server.domain.contribution.repository;

import com.connecteamed.server.domain.contribution.entity.Contribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // 오늘 활동 횟수 조회
    @Query("SELECT COUNT(c) FROM Contribution c " +
            "WHERE c.userId = :userId " +
            "AND FUNCTION('DATE', c.createdAt) = CURRENT_DATE")
    int countTodayActivities(@Param("userId") Long userId);

    // 1년치 데이터 조회
    @Query("SELECT FUNCTION('DATE', c.createdAt) as date, COUNT(c) as count FROM Contribution c " +
            "WHERE c.userId = :userId AND FUNCTION('YEAR', c.createdAt) = :year " +
            "GROUP BY FUNCTION('DATE', c.createdAt)")
    List<ContributionMapping> findAllByUserIdAndYear(@Param("userId") Long userId, @Param("year") int year);

    interface ContributionMapping {
        java.sql.Date getDate();
        Integer getCount();
    }
}
