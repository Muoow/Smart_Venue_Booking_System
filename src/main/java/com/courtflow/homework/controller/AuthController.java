package com.courtflow.homework.controller;

import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.service.AuthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/register")
    public ApiResponse<String> register(@RequestBody Map<String,String> req){

        authService.register(req.get("username"), req.get("password"));

        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody Map<String,String> req){

        String token = authService.login(req.get("username"), req.get("password"));

        return ApiResponse.success(token);
    }
}
