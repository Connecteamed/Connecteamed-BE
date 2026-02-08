package com.connecteamed.server.domain.project.service;

import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.notification.entity.NotificationType;
import com.connecteamed.server.domain.notification.repository.NotificationTypeRepository;
import com.connecteamed.server.domain.notification.service.NotificationCommandService;
import com.connecteamed.server.domain.project.code.ProjectErrorCode;
import com.connecteamed.server.domain.project.dto.ProjectCreateReq;
import com.connecteamed.server.domain.project.dto.ProjectRes;
import com.connecteamed.server.domain.project.dto.ProjectUpdateReq;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.entity.ProjectRequiredRole;
import com.connecteamed.server.domain.project.entity.ProjectRole;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.domain.project.repository.ProjectRequiredRoleRepository;
import com.connecteamed.server.domain.project.repository.ProjectRoleRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final ProjectRequiredRoleRepository projectRequiredRoleRepository;
    private final MemberRepository memberRepository;
    private final Optional<S3Uploader> s3Uploader;
    private final ProjectMemberRepository projectMemberRepository;
    private final NotificationTypeRepository notificationTypeRepository;
    private final NotificationCommandService  notificationCommandService;

    /**
     * 프로젝트 생성
     * @param createReq 프로젝트 생성 요청 (이미지, 이름, 목표, 필요 역할)
     * @param loginId 로그인한 사용자 ID (JWT에서 추출)
     * @return 생성된 프로젝트 정보
     */
    public ProjectRes.CreateResponse createProject(ProjectCreateReq createReq, String loginId) {
        log.info("[ProjectService] createProject called with loginId: {}", loginId);

        // 0. 프로젝트명 중복 체크
        log.debug("[ProjectService] Checking if project name already exists: {}", createReq.getName());
        projectRepository.findByName(createReq.getName()).ifPresent(project -> {
            log.error("[ProjectService] Project name already exists: {}", createReq.getName());
            throw new GeneralException(ProjectErrorCode.PROJECT_NAME_ALREADY_EXISTS);
        });

        // 1. 회원 존재 여부 확인
        log.debug("[ProjectService] Finding member with loginId: {}", loginId);
        Member owner = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> {
                    log.error("[ProjectService] Member not found with loginId: {}", loginId);
                    return new GeneralException(ProjectErrorCode.MEMBER_NOT_FOUND);
                });
        log.info("[ProjectService] Member found: id={}, name={}", owner.getId(), owner.getName());

        String imageUrl = null;
        MultipartFile image = createReq.getImage();
        if (image != null && !image.isEmpty() && s3Uploader.isPresent()) {
            try {
                log.debug("[ProjectService] Uploading image: {}", image.getOriginalFilename());
                imageUrl = s3Uploader.get().upload(image, "project");
                log.info("[ProjectService] Image uploaded successfully: {}", imageUrl);
            } catch (IOException e) {
                log.error("[ProjectService] Image upload failed: ", e);
                throw new GeneralException(ProjectErrorCode.INVALID_REQUEST, "이미지 업로드 실패: " + e.getMessage());
            }
        }

        // 2. 프로젝트 생성
        log.debug("[ProjectService] Creating project with name: {}", createReq.getName());
        Project project = Project.builder()
                .name(createReq.getName())
                .goal(createReq.getGoal())
                .owner(owner)
                .imageUrl(imageUrl)
                .build();

        Project savedProject = projectRepository.save(project);
        log.info("[ProjectService] Project created successfully: id={}, name={}", savedProject.getId(), savedProject.getName());

        ProjectMember projectOwnerAsMember = ProjectMember.builder()
                .project(savedProject)
                .member(owner)
                .build();
        projectMemberRepository.save(projectOwnerAsMember);

        // 3. 필요 역할 등록
        if (createReq.getRequiredRoleNames() != null && !createReq.getRequiredRoleNames().isEmpty()) {
            log.debug("[ProjectService] Registering required roles: {}", createReq.getRequiredRoleNames());
            for (String roleName : createReq.getRequiredRoleNames()) {
                // 역할명으로 ProjectRole 조회, 없으면 자동 생성
                log.debug("[ProjectService] Finding or creating ProjectRole: {}", roleName);
                ProjectRole projectRole = projectRoleRepository.findByRoleName(roleName)
                        .orElseGet(() -> {
                            log.info("[ProjectService] ProjectRole not found, creating new role: {}", roleName);
                            ProjectRole newRole = ProjectRole.builder()
                                    .roleName(roleName)
                                    .build();
                            return projectRoleRepository.save(newRole);
                        });

                // ProjectRequiredRole 생성 및 저장
                ProjectRequiredRole requiredRole = ProjectRequiredRole.builder()
                        .project(savedProject)
                        .projectRole(projectRole)
                        .build();

                projectRequiredRoleRepository.save(requiredRole);
                log.debug("[ProjectService] Required role registered: {}", roleName);
            }
        }

        // 4. 응답 반환
        log.info("[ProjectService] Returning CreateResponse: projectId={}", savedProject.getId());
        return ProjectRes.CreateResponse.builder()
                .projectId(savedProject.getId())
                .createdAt(savedProject.getCreatedAt())
                .build();
    }

    /**
     * 프로젝트 상세 조회
     * @param projectId 프로젝트 ID
     * @return 프로젝트 상세 정보
     */
    @Transactional(readOnly = true)
    public ProjectRes.DetailResponse getProjectDetail(Long projectId) {
        log.info("[ProjectService] getProjectDetail called with projectId: {}", projectId);

        // 1. 프로젝트 조회
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.error("[ProjectService] Project not found with id: {}", projectId);
                    return new GeneralException(ProjectErrorCode.PROJECT_NOT_FOUND);
                });
        log.info("[ProjectService] Project found: id={}, name={}", project.getId(), project.getName());

        // 2. 필요 역할 조회
        List<ProjectRequiredRole> requiredRoles = projectRequiredRoleRepository.findByProjectId(projectId);
        List<String> requiredRoleNames = requiredRoles.stream()
                .map(requiredRole -> requiredRole.getProjectRole().getRoleName())
                .collect(Collectors.toList());
        log.debug("[ProjectService] Required roles: {}", requiredRoleNames);

        // 3. 응답 반환
        return ProjectRes.DetailResponse.builder()
                .projectId(project.getId())
                .name(project.getName())
                .goal(project.getGoal())
                .requiredRoleNames(requiredRoleNames)
                .build();
    }

    /**
     * 프로젝트 수정
     * @param projectId 프로젝트 ID
     * @param updateReq 프로젝트 수정 요청
     * @return 수정된 프로젝트 정보
     */
    public ProjectRes.CreateResponse updateProject(Long projectId, ProjectUpdateReq updateReq) {
        log.info("[ProjectService] updateProject called with projectId: {}", projectId);

        // 1. 프로젝트 존재 여부 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.error("[ProjectService] Project not found with id: {}", projectId);
                    return new GeneralException(ProjectErrorCode.PROJECT_NOT_FOUND);
                });
        log.info("[ProjectService] Project found: id={}, name={}", project.getId(), project.getName());

        // 2. 프로젝트명 중복 체크 (다른 프로젝트와 중복 시)
        if (updateReq.getName() != null && !updateReq.getName().equals(project.getName())) {
            projectRepository.findByName(updateReq.getName()).ifPresent(existingProject -> {
                log.error("[ProjectService] Project name already exists: {}", updateReq.getName());
                throw new GeneralException(ProjectErrorCode.PROJECT_NAME_ALREADY_EXISTS);
            });
        }

        // 3. 프로젝트 기본 정보 수정
        project.updateProject(updateReq.getName(), updateReq.getGoal());
        log.info("[ProjectService] Project basic info updated: name={}, goal={}", project.getName(), project.getGoal());

        // 4. 기존 필요 역할 삭제
        List<ProjectRequiredRole> existingRoles = projectRequiredRoleRepository.findByProjectId(projectId);
        projectRequiredRoleRepository.deleteAll(existingRoles);
        log.debug("[ProjectService] Existing required roles deleted: count={}", existingRoles.size());

        // 5. 새로운 필요 역할 등록
        if (updateReq.getRequiredRoleNames() != null && !updateReq.getRequiredRoleNames().isEmpty()) {
            log.debug("[ProjectService] Registering new required roles: {}", updateReq.getRequiredRoleNames());
            for (String roleName : updateReq.getRequiredRoleNames()) {
                ProjectRole projectRole = projectRoleRepository.findByRoleName(roleName)
                        .orElseThrow(() -> {
                            log.error("[ProjectService] ProjectRole not found: {}", roleName);
                            return new GeneralException(ProjectErrorCode.ROLE_NOT_FOUND);
                        });

                ProjectRequiredRole requiredRole = ProjectRequiredRole.builder()
                        .project(project)
                        .projectRole(projectRole)
                        .build();

                projectRequiredRoleRepository.save(requiredRole);
                log.debug("[ProjectService] Required role registered: {}", roleName);
            }
        }

        // 6. 응답 반환
        log.info("[ProjectService] Returning CreateResponse: projectId={}", project.getId());
        return ProjectRes.CreateResponse.builder()
                .projectId(project.getId())
                .createdAt(project.getCreatedAt())
                .build();
    }

    /**
     * 프로젝트 종료
     * @param projectId 프로젝트 ID
     * @return 종료된 프로젝트 정보
     */
    public ProjectRes.CloseResponse closeProject(Long projectId) {
        log.info("[ProjectService] closeProject called with projectId: {}", projectId);

        // 1. 프로젝트 존재 여부 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.error("[ProjectService] Project not found with id: {}", projectId);
                    return new GeneralException(ProjectErrorCode.PROJECT_NOT_FOUND);
                });
        log.info("[ProjectService] Project found: id={}, name={}", project.getId(), project.getName());

        // 2. 프로젝트 종료
        project.closeProject();
        log.info("[ProjectService] Project closed: id={}, status={}, closedAt={}",
                project.getId(), project.getStatus(), project.getClosedAt());

        // 알림: 프로젝트 종료
        sendProjectCompletionNotification(project);

        // 3. 응답 반환
        return ProjectRes.CloseResponse.builder()
                .projectId(project.getId())
                .status(project.getStatus())
                .closedAt(project.getClosedAt())
                .build();
    }

    // 프로젝트 종료 알림 로직
    private void sendProjectCompletionNotification(Project project) {
        List<ProjectMember> members = projectMemberRepository.findAllByProjectId(project.getId());

        for (ProjectMember pm : members) {
            notificationCommandService.send(
                    pm.getMember(),
                    null,
                    project,
                    null,
                    "PROJECT_COMPLETED"
            );
        }
    }
}

