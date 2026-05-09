package com.courtflow.homework.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.courtflow.homework.common.dto.request.PageQueryRequest;
import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.vo.ReservationVO;
import com.courtflow.homework.service.ReservationService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    @Resource
    private ReservationService reservationService;

    /**
     * 预约场地时间片
     */
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Long>> apply(@RequestBody ReservationApplyRequest request) {
        Long reservationId = reservationService.apply(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reservation created successfully", reservationId));
    }

    /**
     * 取消预约
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id) {
        Boolean cancelled = reservationService.cancel(id);
        if (!cancelled) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failed(400, "Reservation cannot be cancelled"));
        }
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled successfully", null));
    }

    /**
     * 获取预约详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationVO>> getById(@PathVariable Long id) {
        ReservationVO reservation = reservationService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    /**
     * 分页查询用户预约列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<IPage<ReservationVO>>> getByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageQueryRequest request = new PageQueryRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);

        IPage<ReservationVO> result = reservationService.getByUserId(userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
