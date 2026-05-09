package com.courtflow.homework.controller;

import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Date slotDate;

    @BeforeEach
    public void setUp() {
        slotDate = Date.from(LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("预约接口 - 成功申请")
    public void testApply_Success() throws Exception {
        ReservationApplyRequest request = new ReservationApplyRequest();
        request.setUserId(1L);
        request.setVenueId(1L);
        request.setResourceId(1L);
        request.setSlotDate(slotDate);
        request.setStartUnit(10);
        request.setEndUnit(15);
        request.setSize(1);

        MvcResult result = mockMvc.perform(post("/reservation/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        System.out.println("申请预约成功响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("预约接口 - 参数无效")
    public void testApply_InvalidParams() throws Exception {
        ReservationApplyRequest request = new ReservationApplyRequest();
        request.setUserId(null); // 缺少用户ID
        request.setVenueId(1L);
        request.setResourceId(1L);
        request.setSlotDate(slotDate);
        request.setStartUnit(0);
        request.setEndUnit(5);
        request.setSize(1);

        MvcResult result = mockMvc.perform(post("/reservation/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andReturn();

        System.out.println("参数无效响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("预约接口 - 资源不存在")
    public void testApply_ResourceNotFound() throws Exception {
        ReservationApplyRequest request = new ReservationApplyRequest();
        request.setUserId(1L);
        request.setVenueId(1L);
        request.setResourceId(999999L); // 不存在的资源
        request.setSlotDate(slotDate);
        request.setStartUnit(0);
        request.setEndUnit(5);
        request.setSize(1);

        MvcResult result = mockMvc.perform(post("/reservation/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andReturn();

        System.out.println("资源不存在响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("预约接口 - 容量超限")
    public void testApply_SizeExceedsCapacity() throws Exception {
        ReservationApplyRequest request = new ReservationApplyRequest();
        request.setUserId(1L);
        request.setVenueId(1L);
        request.setResourceId(1L);
        request.setSlotDate(slotDate);
        request.setStartUnit(0);
        request.setEndUnit(5);
        request.setSize(999); // 超出容量

        MvcResult result = mockMvc.perform(post("/reservation/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andReturn();

        System.out.println("容量超限响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("取消接口 - 成功取消")
    public void testCancel_Success() throws Exception {
        // 先申请一个预约
        ReservationApplyRequest applyReq = new ReservationApplyRequest();
        applyReq.setUserId(1L);
        applyReq.setVenueId(1L);
        applyReq.setResourceId(1L);
        applyReq.setSlotDate(slotDate);
        applyReq.setStartUnit(10);
        applyReq.setEndUnit(15);
        applyReq.setSize(1);

        MvcResult applyResult = mockMvc.perform(post("/reservation/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = applyResult.getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(responseBody).get("data").asLong();

        // 然后取消这个预约
        MvcResult cancelResult = mockMvc.perform(delete("/reservation/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        System.out.println("取消预约响应: " + cancelResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("取消接口 - 预约不存在")
    public void testCancel_NotFound() throws Exception {
        MvcResult result = mockMvc.perform(delete("/reservation/{id}", 999999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andReturn();

        System.out.println("预约不存在响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("查询接口 - 获取预约详情")
    public void testGetById_Success() throws Exception {
        // 先申请一个预约
        ReservationApplyRequest applyReq = new ReservationApplyRequest();
        applyReq.setUserId(1L);
        applyReq.setVenueId(1L);
        applyReq.setResourceId(1L);
        applyReq.setSlotDate(slotDate);
        applyReq.setStartUnit(20);
        applyReq.setEndUnit(25);
        applyReq.setSize(1);

        MvcResult applyResult = mockMvc.perform(post("/reservation/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = applyResult.getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(responseBody).get("data").asLong();

        // 查询这个预约
        MvcResult queryResult = mockMvc.perform(get("/reservation/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(reservationId))
                .andReturn();

        System.out.println("查询预约详情响应: " + queryResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("查询接口 - 预约不存在")
    public void testGetById_NotFound() throws Exception {
        MvcResult result = mockMvc.perform(get("/reservation/{id}", 999999L))
                .andExpect(status().isOk())
                .andReturn();

        System.out.println("查询不存在预约响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("列表接口 - 获取用户预约列表")
    public void testGetUserReservations_Success() throws Exception {
        MvcResult result = mockMvc.perform(get("/reservation/user/{userId}", 1L)
                .param("pageNumber", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        System.out.println("用户预约列表响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("列表接口 - 分页查询")
    public void testGetUserReservations_Pagination() throws Exception {
        MvcResult result = mockMvc.perform(get("/reservation/user/{userId}", 1L)
                .param("pageNumber", "2")
                .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        System.out.println("分页查询响应: " + result.getResponse().getContentAsString());
    }
}
