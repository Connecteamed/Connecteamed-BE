package com.connecteamed.server.domain.task.exception;

import com.connecteamed.server.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TaskErrorCode implements BaseErrorCode {

    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK404", "업무를 찾을 수 없습니다."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT404", "프로젝트를 찾을 수 없습니다."),
    TASK_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "TASK403", "해당 업무의 담당자가 아니므로 수정할 수 없습니다."),
    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "TASK400_1", "시작일은 마감일보다 늦을 수 없습니다."),
    INVALID_ASSIGNEE(HttpStatus.BAD_REQUEST, "TASK400_2", "담당자 목록이 올바르지 않습니다."),
    ASSIGNEE_NOT_IN_PROJECT(HttpStatus.BAD_REQUEST, "TASK400_3", "프로젝트에 속하지 않은 담당자가 포함되어 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
