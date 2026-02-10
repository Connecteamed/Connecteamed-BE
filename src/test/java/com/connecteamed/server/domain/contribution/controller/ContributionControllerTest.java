package com.connecteamed.server.domain.contribution.controller;

import com.connecteamed.server.domain.contribution.code.ContributionErrorCode;
import com.connecteamed.server.domain.contribution.dto.DailyContributionRes;
import com.connecteamed.server.domain.contribution.dto.ProjectContributionRes;
import com.connecteamed.server.domain.contribution.dto.ProjectMemberContributionRes;
import com.connecteamed.server.domain.contribution.service.ProjectContributionService;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class ContributionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectContributionService projectContributionService;

    private final Long projectId = 1L;


    @Test
    @WithMockUser(username = "test123")
    @DisplayName("팀원별 잔디 조회 성공 - 다수 팀원의 데이터 정합성 검증")
    void getIndividualContributions_MultipleMembers_Success() throws Exception {

        //멤버 1 데이터
        List<DailyContributionRes> dannyContributions = List.of(
                new DailyContributionRes("2026-02-10", 5, 2),
                new DailyContributionRes("2026-02-09", 0, 0)
        );
        ProjectMemberContributionRes member1 = new ProjectMemberContributionRes(1L, "test123", dannyContributions);
        //멤버 2 데이터
        List<DailyContributionRes> member2Contributions = List.of(
                new DailyContributionRes("2026-02-10", 10, 4),
                new DailyContributionRes("2026-02-09", 2, 1)
        );
        ProjectMemberContributionRes member2 = new ProjectMemberContributionRes(2L, "Member2", member2Contributions);

        when(projectContributionService.getProjectMemberContributions(projectId))
                .thenReturn(List.of(member1, member2));

        mockMvc.perform(get("/api/contributions/{projectId}/individual", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))

                // 멤버 1 데이터 검증
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("test123"))
                .andExpect(jsonPath("$.data[0].contributions[0].count").value(5))

                // 멤버 2 데이터 검증
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Member2"))
                .andExpect(jsonPath("$.data[1].contributions[0].count").value(10))
                .andExpect(jsonPath("$.data[1].contributions[0].level").value(4));
    }

    @Test
    @WithMockUser(username = "test123")
    @DisplayName("팀 전체 통계 조회 성공")
    void getEntireContributions_SpringBootTest() throws Exception {
        List<ProjectContributionRes> mockRes = List.of(new ProjectContributionRes("2026-02-10", 5));

        when(projectContributionService.getEntireContributions(projectId)).thenReturn(mockRes);

        mockMvc.perform(get("/api/contributions/{projectId}/entire", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].count").value(5));
    }


    @Test
    @WithMockUser(username = "test123")
    @DisplayName("프로젝트 미존재 시 404 에러 반환")
    void getContributions_NotFound() throws Exception {

        when(projectContributionService.getProjectMemberContributions(anyLong()))
                .thenThrow(new GeneralException(ContributionErrorCode.CONTRIBUTION_PROJECT_NOT_FOUND));

        mockMvc.perform(get("/api/contributions/{projectId}/individual", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("CONTRIBUTION_PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("해당 Id의 프로젝트를 찾을 수 없습니다."));
    }
}
