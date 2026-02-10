package com.connecteamed.server.domain.contribution.code;

import com.connecteamed.server.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ContributionErrorCode implements BaseErrorCode {

    CONTRIBUTION_ERROR_CODE(HttpStatus.BAD_REQUEST,"CONTRIBUTION_ERROR","요청에 실패하였습니다."),
    CONTRIBUTION_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRIBUTION_PROJECT_NOT_FOUND", "해당 Id의 프로젝트를 찾을 수 없습니다."),
    ;


    private final HttpStatus status;
    private final String code;
    private final String message;
}
