package com.courtflow.homework.common.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "Success"),

    BAD_REQUEST(400, "Bad request"),

    UNAUTHORIZED(401, "Unauthorized"),

    FORBIDDEN(403, "Forbidden"),

    NOT_FOUND(404, "Resource not found"),

    CONFLICT(409, "Resource conflict"),

    SERVER_ERROR(500, "Internal server error");

    private final int code;
    private final String message;
}
