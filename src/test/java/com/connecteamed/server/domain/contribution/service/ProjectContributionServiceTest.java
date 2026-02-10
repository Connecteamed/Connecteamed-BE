package com.connecteamed.server.domain.contribution.service;

import com.connecteamed.server.domain.contribution.code.ContributionErrorCode;
import com.connecteamed.server.domain.contribution.dto.DailyContributionRes;
import com.connecteamed.server.domain.contribution.dto.ProjectMemberContributionRes;
import com.connecteamed.server.domain.contribution.repository.ContributionRepository;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectContributionServiceTest {

    @InjectMocks
    private ProjectContributionService projectContributionService;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ContributionRepository contributionRepository;

    private final Long projectId = 1L;

    @Test
    @DisplayName("프로젝트가 존재하지 않으면 에러를 던진다")
    void getContributions_ProjectNotFound() {
        // given
        when(projectRepository.existsById(projectId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> projectContributionService.getProjectMemberContributions(projectId))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("code", ContributionErrorCode.CONTRIBUTION_PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("팀원별 잔디 조회 시 데이터가 없어도 126개의 데이터가 반환되는지 확인")
    void getProjectMemberContributions_CheckExactly126Days() {

        Member mockMember = Member.builder().id(1L).name("test123").build();
        ProjectMember pm = ProjectMember.builder().member(mockMember).build();

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(projectMemberRepository.findAllByProjectId(projectId)).thenReturn(List.of(pm));

        when(contributionRepository.findAllByUserIdInAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<ProjectMemberContributionRes> result = projectContributionService.getProjectMemberContributions(projectId);

        assertThat(result).hasSize(1);

        List<DailyContributionRes> contributions = result.get(0).contributions();

        assertThat(contributions).hasSize(126);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String expectedStartDate = today.minusDays(125).toString();
        String expectedEndDate = today.toString();

        assertThat(contributions.get(0).date()).isEqualTo(expectedStartDate);
        assertThat(contributions.get(125).date()).isEqualTo(expectedEndDate);
    }
}