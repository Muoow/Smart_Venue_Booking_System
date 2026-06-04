package com.courtflow.homework.controller;

import com.courtflow.homework.common.annonation.CheckToken;
import com.courtflow.homework.common.dto.request.LoginRequest;
import com.courtflow.homework.common.dto.request.RegisterRequest;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.service.AuthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CheckToken(required = false)
@RequestMapping("/auth")
public class AuthController {

    private AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody RegisterRequest request){
        authService.register(request == null ? null : request.getUsername(),
                request == null ? null : request.getNickname(),
                request == null ? null : request.getPassword());
        return ApiResponse.success("注册成功。");
    }

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest request){
        String token = authService.login(request == null ? null : request.getUsername(),
                request == null ? null : request.getPassword());
        return ApiResponse.success(token);
    }
}
