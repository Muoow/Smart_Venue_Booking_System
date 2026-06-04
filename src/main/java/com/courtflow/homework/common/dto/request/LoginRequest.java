package com.courtflow.homework.common.dto.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
