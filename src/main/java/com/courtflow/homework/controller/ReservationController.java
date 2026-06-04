package com.courtflow.homework.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.dto.request.PageQueryRequest;
import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.vo.ReservationVO;
import com.courtflow.homework.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/apply")
    public ApiResponse<Long> apply(@RequestBody ReservationApplyRequest request) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            request.setUserId(currentUserId);
        }
        return ApiResponse.success(reservationService.apply(request));
    }

    @GetMapping("/my")
    public ApiResponse<IPage<ReservationVO>> myReservations(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first.");
        }

        PageQueryRequest request = new PageQueryRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        return ApiResponse.success(reservationService.getByUserId(currentUserId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReservationVO> getById(@PathVariable Long id) {
        return ApiResponse.success(reservationService.getById(id));
    }

    @GetMapping("/availability")
    public ApiResponse<Map<String, Object>> availability(
            @RequestParam Long resourceId,
            @RequestParam String slotDate
    ) {
        return ApiResponse.success(reservationService.getAvailability(resourceId, parseSlotDate(slotDate)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable Long id) {
        return ApiResponse.success(reservationService.cancel(id));
    }

    private Date parseSlotDate(String slotDate) {
        if (slotDate == null || slotDate.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约日期不能为空。");
        }
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"}) {
            try {
                return new SimpleDateFormat(pattern, Locale.ROOT).parse(slotDate.trim());
            } catch (ParseException ignored) {
                // Try next pattern.
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "预约日期格式不正确。");
    }
}
