package com.connecteamed.server.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class DashboardRes {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "회고 목록 응답")
    public static class RetrospectiveListRes {
        @JsonProperty("retrospectives")
        @Schema(description = "회고 정보 목록")
        private List<RetrospectiveInfo> retrospectives;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "회고 정보")
    public static class RetrospectiveInfo {
        @JsonProperty("id")
        @Schema(description = "회고 ID", example = "101")
        private Long id;

        @JsonProperty("projectId")
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;

        @JsonProperty("title")
        @Schema(description = "회고 제목", example = "회고 제목입니다")
        private String title;

        @JsonProperty("teamName")
        @Schema(description = "팀/프로젝트 이름", example = "00공모전")
        private String teamName;

        @JsonProperty("writtenDate")
        @Schema(description = "작성 날짜", example = "2025-12-14")
        private LocalDate writtenDate;
    }
}
