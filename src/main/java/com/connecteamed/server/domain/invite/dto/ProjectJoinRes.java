package com.connecteamed.server.domain.invite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectJoinRes {

    @JsonProperty("projectId")
    @Schema(description = "프로젝트 ID", example = "1")
    private Long projectId;
}
