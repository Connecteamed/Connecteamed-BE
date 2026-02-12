package com.connecteamed.server.domain.mypage.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.mypage.code.MyPageErrorCode;
import com.connecteamed.server.domain.mypage.dto.MyPageProjectListRes;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.enums.ProjectStatus;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.auth.exception.AuthException;
import com.connecteamed.server.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MyPageProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberRepository memberRepository;


    /**
     * 완료한 프로젝트 목록
     * @return 완료한 프로젝트 목록 관련 내 정보
     */


    public MyPageProjectListRes.CompletedProjectList getMyCompletedProjects() {

        String loginId = SecurityUtil.getCurrentLoginId();

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(MyPageErrorCode.MEMBER_NOT_FOUND));

        List<ProjectMember> participations = projectMemberRepository.findAllByMember(member);

        List<MyPageProjectListRes.CompletedProjectData> projectDataList = participations.stream()
                .filter(pm -> {
                    Project p = pm.getProject();
                    return p.getStatus() == ProjectStatus.COMPLETED && p.getDeletedAt() == null;
                })
                .map(pm -> {
                    Project p = pm.getProject();

                    List<String> roleNames = pm.getRoles().stream()
                            .map(pmr -> pmr.getRole().getRoleName())
                            .toList();

                    return MyPageProjectListRes.CompletedProjectData.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .roles(roleNames)
                            .createdAt(p.getCreatedAt()) // Instant 그대로 매핑
                            .closedAt(p.getClosedAt())   // Instant 그대로 매핑
                            .build();
                })
                .toList();

        return MyPageProjectListRes.CompletedProjectList.builder()
                .projects(projectDataList)
                .build();
    }



    /**
     * 프로젝트 삭제
     * @param projectId 프로젝트 ID
     * @return 삭제 성공 여부
     */


    @Transactional
    public void deleteCompletedProject(Long projectId) {
        String loginId = SecurityUtil.getCurrentLoginId();
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(MyPageErrorCode.MEMBER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new GeneralException(MyPageErrorCode.PROJECT_NOT_FOUND));

        if (!project.getOwner().getId().equals(member.getId())) {
            throw new AuthException(MyPageErrorCode.PROJECT_NOT_OWNER);
        }

        if (project.getStatus() != ProjectStatus.COMPLETED) {
            throw new GeneralException(MyPageErrorCode.PROJECT_NOT_COMPLETED);
        }

        project.softDelete();
    }

}
