package com.courtflow.homework.common.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(),
                data
        );
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(
                ResultCode.SUCCESS.getCode(),
                message,
                data
        );
    }

    public static <T> ApiResponse<T> fail(ResultCode code) {
        return new ApiResponse<>(
                code.getCode(),
                code.getMessage(),
                null
        );
    }

    public static <T> ApiResponse<T> fail(ResultCode code, String message) {
        return new ApiResponse<>(
                code.getCode(),
                message,
                null
        );
    }
}
