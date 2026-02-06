package com.connecteamed.server.domain.contribution.service;

import com.connecteamed.server.domain.contribution.dto.CalendarContributionRes;
import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.dto.ContributionRes;
import com.connecteamed.server.domain.contribution.dto.DailyContributionRes;
import com.connecteamed.server.domain.contribution.entity.Contribution;
import com.connecteamed.server.domain.contribution.repository.ContributionRepository;
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

    @Transactional
    public ContributionRes recordContribution(Long userId, ContributionReq request) {
        // 중복 체크
        boolean alreadyExists = contributionRepository.existsByUserIdAndActionTypeAndTargetId(
                userId, request.actionType(), request.targetId());

        if (!alreadyExists) {
            contributionRepository.save(Contribution.builder()
                    .userId(userId)
                    .actionType(request.actionType())
                    .targetId(request.targetId())
                    .build());
        }

        // 오늘의 총 활동 횟수 조회 (DB에서 Instant를 날짜로 변환해 카운트)
        LocalDate today = LocalDate.now(KST);
        Instant startOfToday = today.atStartOfDay(KST).toInstant();
        Instant endOfToday = startOfToday.plus(1, ChronoUnit.DAYS);

        int todayCount = contributionRepository.countTodayActivities(userId, startOfToday, endOfToday);

        return new ContributionRes(
                request.actionType(),
                todayCount,
                calculateLevel(todayCount),
                !alreadyExists
        );
    }

    @Transactional(readOnly = true)
    public CalendarContributionRes getCalendar(Long userId, int year) {
        Instant startOfYear = LocalDate.of(year, 1, 1).atStartOfDay(KST).toInstant();
        Instant endOfYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(KST).toInstant();

        Map<LocalDate, Integer> dbData = contributionRepository.findAllByUserIdAndRange(userId, startOfYear, endOfYear).stream()
                .collect(Collectors.toMap(
                        m -> m.getDate().toLocalDate(),
                        ContributionRepository.ContributionMapping::getCount
                ));

        List<DailyContributionRes> contributions = new ArrayList<>();
        LocalDate current = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        int totalYearlyCount = 0;

        // 1월 1일부터 365일 루프
        while (!current.isAfter(end)) {
            int count = dbData.getOrDefault(current, 0);
            totalYearlyCount += count;

            contributions.add(new DailyContributionRes(
                    current.toString(),
                    count,
                    calculateLevel(count)
            ));
            current = current.plusDays(1);
        }

        return new CalendarContributionRes(year, totalYearlyCount, userId, contributions);
    }

    private int calculateLevel(int count) {
        if (count <= 0) return 0;
        if (count <= 2) return 1;
        if (count <= 5) return 2;
        if (count <= 8) return 3;
        return 4;
    }
}