package com.connecteamed.server.domain.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.connecteamed.server.domain.contribution.service.ContributionService;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRequiredRoleRepository;
import com.connecteamed.server.global.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.connecteamed.server.domain.member.entity.Member; // 실제 패키지에 맞게 import 수정
import com.connecteamed.server.domain.project.dto.ProjectMemberRes;
import com.connecteamed.server.domain.project.dto.ProjectMemberRoleUpdateReq;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.entity.ProjectMemberRole;
import com.connecteamed.server.domain.project.entity.ProjectRole;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRoleRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectMemberServiceImplTest {

    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectRoleRepository projectRoleRepository;
    @Mock
    ProjectRequiredRoleRepository projectRequiredRoleRepository;
    @Mock
    ContributionService contributionService;
    @Mock
    MemberRepository memberRepository;

    @InjectMocks ProjectMemberServiceImpl projectMemberService;

    private ProjectMember pm;
    private Member member;
    private List<ProjectMemberRole> roleList;

    private static ProjectRole mockRole(long id, String roleName) {
        ProjectRole r = Mockito.mock(ProjectRole.class);
        Mockito.when(r.getId()).thenReturn(id);
        Mockito.when(r.getRoleName()).thenReturn(roleName);
        return r;
    }

    private static ProjectMemberRole mockPmRole(long roleId, String roleName) {
        ProjectMemberRole pmr = Mockito.mock(ProjectMemberRole.class);
        ProjectRole role = mockRole(roleId, roleName);
        Mockito.when(pmr.getRole()).thenReturn(role);
        return pmr;
    }

    @BeforeEach
    void setUp() {
        pm = Mockito.mock(ProjectMember.class);
        member = Mockito.mock(Member.class);

        // toRes에서 접근하는 값들
        lenient().when(pm.getId()).thenReturn(10L);
        lenient().when(pm.getMember()).thenReturn(member);
        lenient().when(member.getId()).thenReturn(100L);
        lenient().when(member.getName()).thenReturn("tester");

        // 초기 역할 [1,2]
        roleList = new ArrayList<>();
        roleList.add(mockPmRole(1L, "role1"));
        roleList.add(mockPmRole(2L, "role2"));
        lenient().when(pm.getRoles()).thenReturn(new HashSet<>(roleList));

        // 기본 조회 스텁(테스트별로 override 가능)
        lenient().when(projectMemberRepository.findByIdAndProjectId(10L, 1L))
                .thenReturn(Optional.of(pm));
    }

    private void stubFindAllByIdReturn(List<ProjectRole> roles) {
        // findAllById 시그니처가 Iterable이라 anyCollection 말고 Iterable로 잡는 게 안전합니다
        when(projectRoleRepository.findAllById(Mockito.<Iterable<Long>>any()))
                .thenReturn(roles);
    }

    @Test
    @DisplayName("req가 null이면 역할 변경 없음")
    void updateMemberRoles_reqNull_noChange() {
        ProjectMemberRes res = projectMemberService.updateMemberRoles(1L, 10L, null);

        assertEquals(2, roleList.size());
        verify(projectRoleRepository, never()).findAllById(Mockito.<Iterable<Long>>any());
        assertNotNull(res);
    }

    @Test
    @DisplayName("roleIds가 null이면(미전달) 역할 변경 없음")
    void updateMemberRoles_roleIdsNull_noChange() {
        ProjectMemberRoleUpdateReq req = new ProjectMemberRoleUpdateReq(null);

        ProjectMemberRes res = projectMemberService.updateMemberRoles(1L, 10L, req);

        assertEquals(2, roleList.size());
        verify(projectRoleRepository, never()).findAllById(Mockito.<Iterable<Long>>any());
        assertNotNull(res);
    }

    @Test
    @DisplayName("roleIds가 빈 배열이면 전체 삭제")
    void updateMemberRoles_emptyList_clearAll() {
        ProjectMemberRoleUpdateReq req = new ProjectMemberRoleUpdateReq(List.of());

        ProjectMemberRes res = projectMemberService.updateMemberRoles(1L, 10L, req);

        assertEquals(0, roleList.size());
        verify(projectRoleRepository, never()).findAllById(Mockito.<Iterable<Long>>any());
        assertNotNull(res);
        assertTrue(res.roles().isEmpty());
    }

    @Test
    @DisplayName("중복/널 요소 제거 후 role 교체: 요청 [2,3,3,null] => 최종 [2,3]")
    void updateMemberRoles_replaceRoles_dedupeAndReplace() {
        try (MockedStatic<SecurityUtil> mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            String loginId = "test@example.com";
            mockedSecurityUtil.when(SecurityUtil::getCurrentLoginId).thenReturn(loginId);

            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

            ProjectMemberRoleUpdateReq req = new ProjectMemberRoleUpdateReq(Arrays.asList(2L, 3L, 3L, null));

            stubFindAllByIdReturn(List.of(
                    mockRole(2L, "role2"),
                    mockRole(3L, "role3")
            ));

            ProjectMemberRes res = projectMemberService.updateMemberRoles(1L, 10L, req);

            Set<Long> finalIds = new HashSet<>();
            for (ProjectMemberRole pr : roleList) {
                finalIds.add(pr.getRole().getId());
            }

            assertEquals(Set.of(2L, 3L), finalIds);
            assertNotNull(res);
            assertEquals(2, res.roles().size());
        }
    }

    @Test
    @DisplayName("존재하지 않는 roleId가 포함되면 예외")
    void updateMemberRoles_missingRoleId_throws() {
        ProjectMemberRoleUpdateReq req = new ProjectMemberRoleUpdateReq(List.of(2L, 999L));

        stubFindAllByIdReturn(List.of(mockRole(2L, "role2"))); // 999는 없음

        assertThrows(GeneralException.class,
                () -> projectMemberService.updateMemberRoles(1L, 10L, req));
    }

    @Test
    @DisplayName("프로젝트 팀원이 없으면 예외")
    void updateMemberRoles_projectMemberNotFound_throws() {
        when(projectMemberRepository.findByIdAndProjectId(10L, 1L))
                .thenReturn(Optional.empty());

        ProjectMemberRoleUpdateReq req = new ProjectMemberRoleUpdateReq(List.of(1L));

        assertThrows(GeneralException.class,
                () -> projectMemberService.updateMemberRoles(1L, 10L, req));
    }
}
