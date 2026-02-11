package com.connecteamed.server.domain.contribution.service;

import com.connecteamed.server.domain.contribution.code.ContributionErrorCode;
import com.connecteamed.server.domain.contribution.dto.DailyContributionRes;
import com.connecteamed.server.domain.contribution.dto.ProjectContributionRes;
import com.connecteamed.server.domain.contribution.dto.ProjectMemberContributionRes;
import com.connecteamed.server.domain.contribution.entity.Contribution;
import com.connecteamed.server.domain.contribution.repository.ContributionRepository;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectContributionService {
    private final ContributionRepository contributionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Transactional(readOnly = true)
    public List<ProjectMemberContributionRes> getProjectMemberContributions(Long projectId) {

        if (!projectRepository.existsById(projectId)) {
            throw new GeneralException(ContributionErrorCode.CONTRIBUTION_PROJECT_NOT_FOUND);
        }

        // 7*18=126 일의 데이터를 불러오도록 범위 설정
        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = today.minusDays(125);

        Instant startInstant = startDate.atStartOfDay(KST).toInstant();
        Instant endInstant = today.plusDays(1).atStartOfDay(KST).toInstant();

        // 프로젝트 멤버 조회 및 ID 추출
        List<ProjectMember> projectMembers = projectMemberRepository.findAllByProjectId(projectId);
        List<Long> userIds = projectMembers.stream()
                .map(pm -> pm.getMember().getId())
                .toList();

        // 모든 멤버의 잔디 기록 한번에 조회
        Map<Long, Map<LocalDate, Long>> allActivityMap = contributionRepository
                .findAllByProjectIdAndUserIdInAndCreatedAtBetween(projectId,userIds, startInstant, endInstant).stream()
                .collect(Collectors.groupingBy(
                        Contribution::getUserId,
                        Collectors.groupingBy(
                                c -> c.getCreatedAt().atZone(KST).toLocalDate(),
                                Collectors.counting()
                        )
                ));

        // 데이터 조립(db에 빈 날짜는 0으로 채우기)
        return projectMembers.stream().map(pm -> {
            Long userId = pm.getMember().getId();
            Map<LocalDate, Long> userActivity = allActivityMap.getOrDefault(userId, Collections.emptyMap());

            List<DailyContributionRes> contributions = new ArrayList<>();
            for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
                int count = userActivity.getOrDefault(date, 0L).intValue();
                contributions.add(new DailyContributionRes(
                        date.toString(),
                        count,
                        calculateLevel(count)
                ));
            }

            return new ProjectMemberContributionRes(
                    userId,
                    pm.getMember().getName(),
                    contributions
            );
        }).toList();
    }



    @Transactional(readOnly = true)
    public List<ProjectContributionRes> getEntireContributions(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new GeneralException(ContributionErrorCode.CONTRIBUTION_PROJECT_NOT_FOUND);
        }

        //2주로 범위 설정
        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = today.minusDays(13); // 오늘 포함 14일

        Instant startInstant = startDate.atStartOfDay(KST).toInstant();
        Instant endInstant = today.plusDays(1).atStartOfDay(KST).toInstant();
        

        // 잔디 기록 합산
        Map<LocalDate, Long> teamActivityMap = contributionRepository
                .findAllByProjectIdAndCreatedAtBetween(projectId, startInstant, endInstant).stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCreatedAt().atZone(KST).toLocalDate(),
                        Collectors.counting()
                ));

        // 데이터 조립
        List<ProjectContributionRes> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            int totalCount = teamActivityMap.getOrDefault(date, 0L).intValue();
            result.add(new ProjectContributionRes(date.toString(), totalCount));
        }

        return result;
    }




    private int calculateLevel(int count) {
        if (count <= 0) return 0;
        if (count <= 2) return 1;
        if (count <= 5) return 2;
        if (count <= 8) return 3;
        return 4;
    }
}