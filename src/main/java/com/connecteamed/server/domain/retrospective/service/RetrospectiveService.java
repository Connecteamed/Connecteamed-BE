package com.connecteamed.server.domain.retrospective.service;

import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.domain.retrospective.dto.*;
import com.connecteamed.server.domain.retrospective.entity.AiRetrospective;
import com.connecteamed.server.domain.retrospective.repository.AiRetrospectiveRepository;
import com.connecteamed.server.domain.retrospective.repository.RetrospectiveRepository;
import com.connecteamed.server.domain.task.entity.Task;
import com.connecteamed.server.domain.task.repository.TaskRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrospectiveService {

    private final AiRetrospectiveRepository aiRetrospectiveRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final RetrospectiveAsyncService retrospectiveAsyncService;

    // ai 회고 생성
    @Transactional
    public RetrospectiveCreateRes createAiRetrospective(Long projectId, Long memberId, RetrospectiveCreateReq request){

        Project project = projectRepository.findByIdWithDetails(projectId)
                .orElseThrow(() -> new RuntimeException("프로젝트를 찾을 수 없습니다."));

        ProjectMember writer = project.getProjectMembers().stream()
                .filter(pm -> pm.getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("팀원 정보를 찾을 수 없습니다."));

        List<Task> selectedTasks = taskRepository.findAllById(request.taskIds());

        String otherTasks = project.getProjectMembers().stream()
                .filter(pm -> !pm.getId().equals(writer.getId()))
                .flatMap(pm -> pm.getTasks().stream())
                .map(Task::getName)
                .distinct()
                .collect(Collectors.joining(", "));

        if (otherTasks.isEmpty()) {
            otherTasks = "현재 등록된 다른 팀원의 업무가 없습니다";
        }

        // 내 업무 리스트 상세 포맷팅
        String myTaskList = selectedTasks.stream()
                .map(t -> String.format("- 업무명: %s / 내용: %s / 성과: %s", t.getName(), t.getContent(), t.getResult()))
                .collect(Collectors.joining("\n"));

        // 내 역할 정보 추출
        String myRole = writer.getRoles().stream()
                .map(pmr -> pmr.getRole().getRoleName())
                .collect(Collectors.joining(", "));
        if (myRole.isEmpty()) myRole = "팀원";

        // AI 분석 수행
        AiRetrospective retrospective = AiRetrospective.builder()
                .project(project)
                .writer(writer)
                .title(request.title())
                .projectResult("AI 분석이 진행 중입니다. 잠시만 기다려 주세요.")
                .build();

        selectedTasks.forEach(retrospective::addRetrospectiveTask);
        AiRetrospective saved = aiRetrospectiveRepository.save(retrospective);

        retrospectiveAsyncService.processAiAnalysis(
                saved.getId(),
                project.getName(),
                project.getGoal(),
                request.title(),
                request.projectResult(),
                myRole,
                myTaskList,
                otherTasks
        );

        return new RetrospectiveCreateRes(saved.getId(), saved.getTitle());
    }

    // ai 회고 상세 조회
    public RetrospectiveDetailRes getRetrospectiveDetail(Long projectId, Long retrospectiveId) {
        AiRetrospective retrospective = aiRetrospectiveRepository.findByIdAndProjectId(retrospectiveId, projectId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        return new RetrospectiveDetailRes(
                retrospective.getId(),
                retrospective.getTitle(),
                retrospective.getProjectResult(),
                retrospective.getCreatedAt(),
                retrospective.getWriter().getId()
        );
    }

    // ai 회고 목록 조회
    public RetrospectiveListRes getRetrospectivesByProject(Long projectId) {
        // 프로젝트 ID로 회고 목록 조회
        List<AiRetrospective> retrospectives = aiRetrospectiveRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId);

        List<RetrospectiveListRes.RetrospectiveSummary> summaries = retrospectives.stream()
                .map(r -> new RetrospectiveListRes.RetrospectiveSummary(
                        r.getId(),
                        r.getTitle(),
                        r.getCreatedAt()))
                .collect(Collectors.toList());

        return new RetrospectiveListRes(summaries);
    }

    // 회고 수정
    @Transactional
    public void updateRetrospective(Long memberId, Long projectId, Long retrospectiveId, RetrospectiveUpdateReq request) {
        AiRetrospective retrospective = aiRetrospectiveRepository.findByIdAndProjectId(retrospectiveId, projectId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        if (!retrospective.getWriter().getMember().getId().equals(memberId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        retrospective.update(request.title(), request.projectResult());
    }

    // 회고 삭제
    @Transactional
    public void deleteRetrospective(Long memberId, Long projectId, Long retrospectiveId) {
        AiRetrospective retrospective = aiRetrospectiveRepository.findByIdAndProjectId(retrospectiveId, projectId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        if (!retrospective.getWriter().getMember().getId().equals(memberId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

        aiRetrospectiveRepository.delete(retrospective);
    }
}
