package com.connecteamed.server.domain.contribution.dto;

import java.util.List;

public record ProjectMemberContributionRes(
        Long id,
        String name,
        List<DailyContributionRes> contributions
) {}
