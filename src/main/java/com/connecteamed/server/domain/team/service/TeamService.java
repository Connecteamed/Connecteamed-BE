package com.connecteamed.server.domain.team.service;

import com.connecteamed.server.domain.member.code.MemberErrorCode;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.mypage.code.MyPageErrorCode;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.enums.ProjectStatus;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.team.code.TeamErrorCode;
import com.connecteamed.server.domain.team.dto.TeamListRes;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional
public class TeamService {

    private final MemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public TeamListRes.TeamDataList getMyProjectTeams() {
        String loginId = SecurityUtil.getCurrentLoginId();
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.MEMBER_NOT_FOUND));

        List<ProjectMember> projectMembers = projectMemberRepository.findAllByMemberIdWithProject(member.getId());

        List<TeamListRes.TeamInfo> teams = projectMembers.stream()
                .map(ProjectMember::getProject)
                .filter(project -> project.getDeletedAt() == null &&
                        project.getStatus() == ProjectStatus.IN_PROGRESS)
                .map(project -> TeamListRes.TeamInfo.builder()
                        .teamId(project.getId())
                        .name(project.getName())
                        .build())
                .toList();

        return new TeamListRes.TeamDataList(teams);

    }
}
