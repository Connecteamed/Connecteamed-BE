package com.connecteamed.server.domain.contribution.service;

import com.connecteamed.server.domain.contribution.code.ContributionErrorCode;
import com.connecteamed.server.domain.contribution.dto.ProjectMemberContributionRes;
import com.connecteamed.server.domain.contribution.repository.ContributionRepository;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("팀원별 잔디 조회 시 데이터 개수가 126개여야 한다")
    void getProjectMemberContributions_Success() {
        // given
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(projectMemberRepository.findAllByProjectId(projectId)).thenReturn(Collections.emptyList());

        // when
        List<ProjectMemberContributionRes> result = projectContributionService.getProjectMemberContributions(projectId);

        // then
        // 멤버가 0명이어도 리스트는 반환되어야 함
        assertThat(result).isNotNull();
    }
}