package com.connecteamed.server.domain.contribution.code;


import com.connecteamed.server.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ContributionSuccessCode implements BaseSuccessCode {


    CONTRIBUTION_OK(HttpStatus.OK,"CONTRIBUTION_OK","요청에 성공하였습니다."),
    ;


    private final HttpStatus status;
    private final String code;
    private final String message;
}
