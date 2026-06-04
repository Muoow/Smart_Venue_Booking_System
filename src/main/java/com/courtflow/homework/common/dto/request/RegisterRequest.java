package com.courtflow.homework.common.dto.request;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String nickname;
    private String password;
}
