package com.connecteamed.server.domain.project.repository;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    @EntityGraph(attributePaths = {"member", "roles", "roles.role"})
    List<ProjectMember> findAllByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"member", "roles", "roles.role"})
    Optional<ProjectMember> findByIdAndProjectId(Long id, Long projectId);

    @EntityGraph(attributePaths = {"member", "roles", "roles.role"})
    List<ProjectMember> findAllByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"project", "roles", "roles.role"})
    List<ProjectMember> findAllByMember(Member member);

    @EntityGraph(attributePaths = {"member"})
    List<ProjectMember> findAllByProject_IdAndMember_IdIn(Long projectId, List<Long> memberIds);

    //사용자가 참여중인 프로젝트 목록 조회 위한 query
    @Query("SELECT pm FROM ProjectMember pm " +
            "JOIN FETCH pm.project p " +
            "WHERE pm.member.id = :memberId " +
            "AND p.deletedAt IS NULL")
    List<ProjectMember> findAllByMemberIdWithProject(Long memberId);

    Optional<ProjectMember> findByProject_IdAndMember_Id(Long projectId, Long memberId);

    boolean existsByProjectIdAndMemberId(Long projectId, Long memberId);

    @Query("SELECT COUNT(pm) > 0 " +
           "FROM ProjectMember pm " +
           "WHERE pm.project.id = :projectId " +
           "AND pm.member.loginId = :loginId")
    boolean existsByProjectIdAndMemberLoginId(@Param("projectId") Long projectId, 
                                              @Param("loginId") String loginId);
}
