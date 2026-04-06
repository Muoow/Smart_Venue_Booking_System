package com.courtflow.homework.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.courtflow.homework.common.dto.request.PageQueryRequest;
import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.courtflow.homework.common.vo.ReservationVO;

public interface ReservationService {

    Long apply(ReservationApplyRequest request);

    Boolean cancel(Long id);

    ReservationVO getById(Long id);

    IPage<ReservationVO> getByUserId(Long userId, PageQueryRequest request);

}
