package com.connecteamed.server.domain.contribution.service;

import com.connecteamed.server.domain.contribution.dto.CalendarContributionRes;
import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.dto.ContributionRes;
import com.connecteamed.server.domain.contribution.dto.DailyContributionRes;
import com.connecteamed.server.domain.contribution.entity.Contribution;
import com.connecteamed.server.domain.contribution.repository.ContributionRepository;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;
import com.connecteamed.server.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContributionService {
    private final ContributionRepository contributionRepository;
    private final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final SecurityUtil securityUtil;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ContributionRes recordContribution(Long userId, Long projectId, ContributionReq request) {
        System.out.println("=== 기여도 기록 시작 ===");
        System.out.println("유저ID: " + userId + " | 타겟ID: " + request.targetId());

        // 중복 체크
        boolean alreadyExists = contributionRepository.existsByUserIdAndActionTypeAndTargetId(
                userId, request.actionType(), request.targetId());

        System.out.println("중복 여부: " + alreadyExists);

        if (!alreadyExists) {
            Contribution saved = contributionRepository.saveAndFlush(Contribution.builder()
                    .userId(userId)
                    .projectId(projectId)
                    .actionType(request.actionType())
                    .targetId(request.targetId())
                    .build());

            System.out.println("데이터 저장 완료: ID " + saved.getId());
        }

        // 오늘의 총 활동 횟수 조회 (DB에서 Instant를 날짜로 변환해 카운트)
        LocalDate today = LocalDate.now(KST);
        Instant startInstant = today.atStartOfDay(KST).toInstant();
        Instant endInstant = today.plusDays(1).atStartOfDay(KST).toInstant();

        List<Contribution> todayActivities = contributionRepository.findAllByUserIdAndRange(userId, startInstant, endInstant);

        System.out.println("조회된 리스트 크기: " + todayActivities.size());
        for(Contribution c : todayActivities) {
            System.out.println("저장된 데이터 시간: " + c.getCreatedAt() + " | 유저: " + c.getUserId());
        }

        int todayCount = (int) todayActivities.stream()
                .filter(c -> c.getCreatedAt().atZone(KST).toLocalDate().equals(today))
                .count();

        System.out.println("최종 카운트: " + todayCount);
        System.out.println("=== 기여도 기록 종료 ===");

        return new ContributionRes(
                request.actionType(),
                todayCount,
                calculateLevel(todayCount),
                !alreadyExists
        );
    }

    @Transactional(readOnly = true)
    public CalendarContributionRes getCalendar(Long projectId, int year) {
        Long authId = securityUtil.getCurrentMemberId();
        ProjectMember pm = projectMemberRepository.findByProject_IdAndMember_Id(projectId, authId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));

        Long realMemberId = pm.getMember().getId();
        System.out.println("조회 요청 인증ID: " + authId + " -> 실제 변환 ID: " + realMemberId);

        Instant startOfYear = LocalDate.of(year, 1, 1).atStartOfDay(KST).toInstant();
        Instant endOfYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(KST).toInstant();

        Map<LocalDate, Long> dbData = contributionRepository.findAllByUserIdAndRange(realMemberId, startOfYear, endOfYear).stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCreatedAt().atZone(KST).toLocalDate(),
                        Collectors.counting()
                ));

        List<DailyContributionRes> contributions = new ArrayList<>();
        LocalDate current = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        int totalYearlyCount = 0;

        while (!current.isAfter(end)) {
            int count = dbData.getOrDefault(current, 0L).intValue();
            totalYearlyCount += count;

            contributions.add(new DailyContributionRes(
                    current.toString(),
                    count,
                    calculateLevel(count)
            ));
            current = current.plusDays(1);
        }

        return new CalendarContributionRes(year, totalYearlyCount, realMemberId, contributions);
    }

    private int calculateLevel(int count) {
        if (count <= 0) return 0;
        if (count <= 2) return 1;
        if (count <= 5) return 2;
        if (count <= 8) return 3;
        return 4;
    }
}