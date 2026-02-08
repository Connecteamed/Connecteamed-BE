package com.connecteamed.server.domain.project.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.enums.SocialType;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.code.ProjectErrorCode;
import com.connecteamed.server.domain.project.dto.ProjectCreateReq;
import com.connecteamed.server.domain.project.dto.ProjectRes;
import com.connecteamed.server.domain.project.dto.ProjectUpdateReq;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectRequiredRole;
import com.connecteamed.server.domain.project.entity.ProjectRole;
import com.connecteamed.server.domain.project.enums.ProjectStatus;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.domain.project.repository.ProjectRequiredRoleRepository;
import com.connecteamed.server.domain.project.repository.ProjectRoleRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.S3Uploader;
import com.connecteamed.server.global.util.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService 테스트")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectRoleRepository projectRoleRepository;

    @Mock
    private ProjectRequiredRoleRepository projectRequiredRoleRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Uploader s3Uploader;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private static MockedStatic<SecurityUtil> mockedSecurityUtil;

    @InjectMocks
    private ProjectService projectService;

    private Member testMember;
    private Project testProject;
    private ProjectRole designerRole;
    private ProjectRole serverRole;
    private List<String> requiredRoleNames;

    @BeforeAll
    static void setup() {
        mockedSecurityUtil = mockStatic(SecurityUtil.class);
    }

    @AfterAll
    static void tearDown() {
        mockedSecurityUtil.close();
    }

    @BeforeEach
    void setUp() {
        // 테스트용 멤버 생성
        testMember = Member.builder()
                .id(1L)
                .publicId(UUID.randomUUID())
                .name("테스트사용자")
                .loginId("test@example.com")
                .socialType(SocialType.LOCAL)
                .build();

        // 테스트용 프로젝트 생성
        testProject = Project.builder()
                .id(1L)
                .publicId(UUID.randomUUID())
                .owner(testMember)
                .name("UMC 7기")
                .goal("앱 런칭")
                .imageUrl(null)
                .build();
        ReflectionTestUtils.setField(testProject, "createdAt", Instant.now());
        ReflectionTestUtils.setField(testProject, "updatedAt", Instant.now());

        // 테스트용 역할 생성
        designerRole = ProjectRole.builder()
                .id(1L)
                .roleName("DESIGNER")
                .build();

        serverRole = ProjectRole.builder()
                .id(2L)
                .roleName("SERVER")
                .build();

        requiredRoleNames = Arrays.asList("DESIGNER", "SERVER", "ANDROID");
    }

    @Test
    @DisplayName("프로젝트 생성 성공")
    void createProject_Success() {
        // given
        ProjectCreateReq createReq = ProjectCreateReq.builder()
                .name("UMC 7기")
                .goal("앱 런칭")
                .requiredRoleNames(requiredRoleNames)
                .build();

        when(projectRepository.findByName("UMC 7기")).thenReturn(Optional.empty());
        when(memberRepository.findByLoginId("test@example.com")).thenReturn(Optional.of(testMember));
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);
        when(projectRoleRepository.findByRoleName("DESIGNER")).thenReturn(Optional.of(designerRole));
        when(projectRoleRepository.findByRoleName("SERVER")).thenReturn(Optional.of(serverRole));
        when(projectRoleRepository.findByRoleName("ANDROID")).thenReturn(Optional.of(
                ProjectRole.builder().id(3L).roleName("ANDROID").build()
        ));

        // when
        ProjectRes.CreateResponse response = projectService.createProject(createReq, "test@example.com");

        // then
        assertNotNull(response);
        assertEquals(1L, response.getProjectId());
        assertNotNull(response.getCreatedAt());
        verify(projectRepository, times(1)).save(any(Project.class));
        verify(projectRequiredRoleRepository, times(3)).save(any(ProjectRequiredRole.class));
    }

    @Test
    @DisplayName("프로젝트 생성 실패 - 프로젝트명 중복")
    void createProject_Fail_DuplicateName() {
        // given
        ProjectCreateReq createReq = ProjectCreateReq.builder()
                .name("UMC 7기")
                .goal("앱 런칭")
                .requiredRoleNames(requiredRoleNames)
                .build();

        when(projectRepository.findByName("UMC 7기")).thenReturn(Optional.of(testProject));

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () ->
                projectService.createProject(createReq, "test@example.com")
        );
        assertEquals(ProjectErrorCode.PROJECT_NAME_ALREADY_EXISTS, exception.getCode());
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("프로젝트 생성 실패 - 회원 없음")
    void createProject_Fail_MemberNotFound() {
        // given
        ProjectCreateReq createReq = ProjectCreateReq.builder()
                .name("UMC 7기")
                .goal("앱 런칭")
                .requiredRoleNames(requiredRoleNames)
                .build();

        when(projectRepository.findByName("UMC 7기")).thenReturn(Optional.empty());
        when(memberRepository.findByLoginId("test@example.com")).thenReturn(Optional.empty());

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () ->
                projectService.createProject(createReq, "test@example.com")
        );
        assertEquals(ProjectErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("프로젝트 생성 성공 - 역할이 없으면 자동 생성 후 필요 역할 등록")
    void createProject_Success_AutoCreateRole() {
        // given
        ProjectCreateReq createReq = ProjectCreateReq.builder()
                .name("UMC 7기")
                .goal("앱 런칭")
                .requiredRoleNames(Arrays.asList("INVALID_ROLE"))
                .build();

        when(projectRepository.findByName("UMC 7기")).thenReturn(Optional.empty());
        when(memberRepository.findByLoginId("test@example.com")).thenReturn(Optional.of(testMember));
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        // 역할이 없다고 가정
        when(projectRoleRepository.findByRoleName("INVALID_ROLE")).thenReturn(Optional.empty());

        // 자동 생성되는 역할 save 결과 준비
        ProjectRole createdRole = ProjectRole.builder().id(99L).roleName("INVALID_ROLE").build();
        when(projectRoleRepository.save(any(ProjectRole.class))).thenReturn(createdRole);

        // when
        ProjectRes.CreateResponse response = projectService.createProject(createReq, "test@example.com");

        // then
        assertNotNull(response);
        assertEquals(1L, response.getProjectId());

        verify(projectRoleRepository, times(1)).save(any(ProjectRole.class));              // 역할 자동 생성
        verify(projectRequiredRoleRepository, times(1)).save(any(ProjectRequiredRole.class)); // 필요역할 등록
    }

    @Test
    @DisplayName("프로젝트 상세 조회 성공")
    void getProjectDetail_Success() {
        // given
        ProjectRequiredRole requiredRole1 = ProjectRequiredRole.builder()
                .id(1L)
                .project(testProject)
                .projectRole(designerRole)
                .build();

        ProjectRequiredRole requiredRole2 = ProjectRequiredRole.builder()
                .id(2L)
                .project(testProject)
                .projectRole(serverRole)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRequiredRoleRepository.findByProjectId(1L))
                .thenReturn(Arrays.asList(requiredRole1, requiredRole2));

        // when
        ProjectRes.DetailResponse response = projectService.getProjectDetail(1L);

        // then
        assertNotNull(response);
        assertEquals(1L, response.getProjectId());
        assertEquals("UMC 7기", response.getName());
        assertEquals("앱 런칭", response.getGoal());
        assertEquals(2, response.getRequiredRoleNames().size());
        assertTrue(response.getRequiredRoleNames().contains("DESIGNER"));
        assertTrue(response.getRequiredRoleNames().contains("SERVER"));
    }

    @Test
    @DisplayName("프로젝트 상세 조회 실패 - 프로젝트 없음")
    void getProjectDetail_Fail_ProjectNotFound() {
        // given
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () ->
                projectService.getProjectDetail(1L)
        );
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("프로젝트 수정 성공")
    void updateProject_Success() {
        // given
        ProjectUpdateReq updateReq = ProjectUpdateReq.builder()
                .name("UMC 8기")
                .goal("서비스 런칭")
                .requiredRoleNames(Arrays.asList("DESIGNER", "SERVER"))
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.findByName("UMC 8기")).thenReturn(Optional.empty());
        when(projectRequiredRoleRepository.findByProjectId(1L)).thenReturn(new ArrayList<>());
        when(projectRoleRepository.findByRoleName("DESIGNER")).thenReturn(Optional.of(designerRole));
        when(projectRoleRepository.findByRoleName("SERVER")).thenReturn(Optional.of(serverRole));

        // when
        ProjectRes.CreateResponse response = projectService.updateProject(1L, updateReq);

        // then
        assertNotNull(response);
        assertEquals(1L, response.getProjectId());
        verify(projectRequiredRoleRepository, times(1)).deleteAll(any());
        verify(projectRequiredRoleRepository, times(2)).save(any(ProjectRequiredRole.class));
    }

    @Test
    @DisplayName("프로젝트 수정 실패 - 프로젝트 없음")
    void updateProject_Fail_ProjectNotFound() {
        // given
        ProjectUpdateReq updateReq = ProjectUpdateReq.builder()
                .name("UMC 8기")
                .goal("서비스 런칭")
                .requiredRoleNames(Arrays.asList("DESIGNER"))
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () ->
                projectService.updateProject(1L, updateReq)
        );
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("프로젝트 수정 실패 - 프로젝트명 중복")
    void updateProject_Fail_DuplicateName() {
        // given
        Project anotherProject = Project.builder()
                .id(2L)
                .publicId(UUID.randomUUID())
                .owner(testMember)
                .name("UMC 8기")
                .goal("다른 목표")
                .build();

        ProjectUpdateReq updateReq = ProjectUpdateReq.builder()
                .name("UMC 8기")
                .goal("서비스 런칭")
                .requiredRoleNames(Arrays.asList("DESIGNER"))
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.findByName("UMC 8기")).thenReturn(Optional.of(anotherProject));

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () ->
                projectService.updateProject(1L, updateReq)
        );
        assertEquals(ProjectErrorCode.PROJECT_NAME_ALREADY_EXISTS, exception.getCode());
    }

    @Test
    @DisplayName("프로젝트 종료 성공")
    void closeProject_Success() {
        // given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        // when
        ProjectRes.CloseResponse response = projectService.closeProject(1L);

        // then
        assertNotNull(response);
        assertEquals(1L, response.getProjectId());
        assertEquals(ProjectStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getClosedAt());
    }

    @Test
    @DisplayName("프로젝트 종료 실패 - 프로젝트 없음")
    void closeProject_Fail_ProjectNotFound() {
        // given
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () ->
                projectService.closeProject(1L)
        );
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
    }
}

