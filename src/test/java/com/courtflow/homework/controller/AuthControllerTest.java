package com.courtflow.homework.controller;

import com.courtflow.homework.common.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("注册接口 - 成功")
    public void testRegister_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("username", "user_" + System.currentTimeMillis());
        request.put("password", "password123");

        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        System.out.println("Register响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("注册接口 - 用户名重复")
    public void testRegister_DuplicateUsername() throws Exception {
        String username = "duplicate_user_" + System.currentTimeMillis();

        // 第一次注册
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("password", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 第二次注册相同用户名
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andReturn();

        System.out.println("重复注册响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("登录接口 - 成功")
    public void testLogin_Success() throws Exception {
        String username = "login_user_" + System.currentTimeMillis();
        String password = "password123";

        // 先注册
        Map<String, String> registerReq = new HashMap<>();
        registerReq.put("username", username);
        registerReq.put("password", password);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        // 然后登录
        Map<String, String> loginReq = new HashMap<>();
        loginReq.put("username", username);
        loginReq.put("password", password);

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        System.out.println("登录成功响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("登录接口 - 密码错误")
    public void testLogin_WrongPassword() throws Exception {
        String username = "test_user_" + System.currentTimeMillis();

        // 先注册
        Map<String, String> registerReq = new HashMap<>();
        registerReq.put("username", username);
        registerReq.put("password", "correctpass");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        // 用错误密码登录
        Map<String, String> loginReq = new HashMap<>();
        loginReq.put("username", username);
        loginReq.put("password", "wrongpass");

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andReturn();

        System.out.println("登录失败响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("登录接口 - 用户不存在")
    public void testLogin_UserNotFound() throws Exception {
        Map<String, String> loginReq = new HashMap<>();
        loginReq.put("username", "nonexistent_user_" + System.currentTimeMillis());
        loginReq.put("password", "password123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andReturn();

        System.out.println("用户不存在响应: " + result.getResponse().getContentAsString());
    }
}
