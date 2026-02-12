package com.connecteamed.server.domain.invite.service;

import com.connecteamed.server.domain.invite.code.InviteErrorCode;
import com.connecteamed.server.domain.invite.dto.InviteCodeRes;
import com.connecteamed.server.domain.invite.dto.ProjectJoinRes;
import com.connecteamed.server.domain.invite.entity.InviteCode;
import com.connecteamed.server.domain.invite.repository.InviteCodeRepository;
import com.connecteamed.server.domain.member.code.MemberErrorCode;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.code.ProjectErrorCode;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InviteService {

    private final InviteCodeRepository inviteCodeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberRepository memberRepository;


    //초대 코드 요청자에게 제공 로직
    @Transactional
    public InviteCodeRes getOrGenerateInviteCode(Long projectId, String loginId) {

        // 해당 member 존재하는지 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.MEMBER_NOT_FOUND));

        projectRepository.findById(projectId).orElseThrow(() -> new GeneralException(ProjectErrorCode.PROJECT_NOT_FOUND));

        //요청자가 해당 프로젝트의 멤버인지 확인
        validateProjectMember(projectId, member.getId());

        //유효한 기존 코드 조회
        Optional<InviteCode> validCodeOpt = inviteCodeRepository
                .findTopByProjectIdAndExpiredAtAfterOrderByCreatedAtDesc(projectId, Instant.now());

        if (validCodeOpt.isPresent()) {
            InviteCode existingCode = validCodeOpt.get();
            // 만료 시각이 1시간 이상 남았을 때 -> 재사용
            if (existingCode.getExpiredAt().minus(Duration.ofHours(1)).isAfter(Instant.now())) {
                log.info("[InviteService] Reusing existing code for project: {}", projectId);
                return InviteCodeRes.builder()
                        .inviteCode(existingCode.getCode())
                        .expiredAt(existingCode.getExpiredAt())
                        .build();
            }
        }

        // 기존 코드 없거나 1시간 미만인 유효코드 만 있을때 -> 새로 생성해서 return
        InviteCode newInvite = createNewInviteCode(projectId);
        return InviteCodeRes.builder()
                .inviteCode(newInvite.getCode())
                .expiredAt(newInvite.getExpiredAt())
                .build();
    }

    //입장 로직
    @Transactional
    public ProjectJoinRes joinProjectByCode(String code, String loginId) {

        InviteCode inviteCode = inviteCodeRepository.findByCodeAndExpiredAtAfter(code, Instant.now())
                .orElseThrow(() -> new GeneralException(InviteErrorCode.INVALID_INVITE_CODE));

        Project project = inviteCode.getProject();



        // 해당 member 존재하는지 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.MEMBER_NOT_FOUND));

        // 이미 프로젝트(팀) 멤버로 들어가 있는 경우
        if (projectMemberRepository.existsByProjectIdAndMemberId(project.getId(), member.getId())) {
            throw new GeneralException(InviteErrorCode.INVITE_ALREADY_INVITED);
        }

        //프로젝트 멤버로 추가
        ProjectMember projectMember = ProjectMember.builder()
                .project(project)
                .member(member)
                .build();

        projectMemberRepository.save(projectMember);

        return ProjectJoinRes.builder()
                .projectId(project.getId())
                .build();
    }

    //새로운 초대 코드 생성
    private InviteCode createNewInviteCode(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new GeneralException(ProjectErrorCode.PROJECT_NOT_FOUND));

        String code;
        int retryCount = 0;
        int maxRetries = 5;

        // 중복되지 않는 코드가 생성될 때까지 반복
        do {
            code = UUID.randomUUID().toString().substring(0, 8);
            retryCount++;
            if (retryCount > maxRetries) {
                log.error("[InviteService] Failed to generate unique code after {} retries", maxRetries);
                throw new GeneralException(InviteErrorCode.INVITE_CODE_GENERATION_FAILED);
            }
        } while (inviteCodeRepository.existsByCode(code));

        InviteCode inviteCode = InviteCode.builder()
                .project(project)
                .code(code)
                .expiredAt(Instant.now().plus(Duration.ofDays(1)))
                .build();
        inviteCodeRepository.save(inviteCode);
        return inviteCode;
    }

    //발급 요청자가 해당 프로젝트의 멤버에 속해 있는지 확인(초대 권한여부를 확인하기 위해)
    private void validateProjectMember(Long projectId, Long memberId) {
        if (!projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new GeneralException(InviteErrorCode.INVITE_UNAUTHORIZED_MEMBER);
        }
    }

}
