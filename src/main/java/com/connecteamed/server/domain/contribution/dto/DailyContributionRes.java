package com.connecteamed.server.domain.contribution.dto;

public record DailyContributionRes (
        String date,
        int count,
        int level
) {}
