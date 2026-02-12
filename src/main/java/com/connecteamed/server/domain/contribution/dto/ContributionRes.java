package com.connecteamed.server.domain.contribution.dto;

import com.connecteamed.server.domain.contribution.enums.ContributionAction;

public record ContributionRes (
        ContributionAction actionType,
        int todayCount,
        int currentLevel,
        boolean isIncremented
) {}
