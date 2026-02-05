package com.connecteamed.server.domain.contribution.dto;

import com.connecteamed.server.domain.contribution.enums.ContributionAction;

public record ContributionReq (
        ContributionAction actionType,
        Long targetId
) {}
