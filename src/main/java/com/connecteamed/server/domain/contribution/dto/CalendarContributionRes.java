package com.connecteamed.server.domain.contribution.dto;

import java.util.List;

public record CalendarContributionRes (
        int year,
        int totalActivityCount,
        Long userId,
        List<DailyContributionRes> contributions
) {}
