package com.courtflow.homework.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.courtflow.homework.common.dto.request.PageQueryRequest;
import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.courtflow.homework.common.vo.ReservationVO;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.TimeSlotMapper;
import com.courtflow.homework.service.ReservationService;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;

    private final TimeSlotMapper timeSlotMapper;

    private final OrderMapper orderMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Autowired
    public ReservationServiceImpl(ReservationMapper reservationMapper, TimeSlotMapper timeSlotMapper, OrderMapper orderMapper) {
        this.reservationMapper = reservationMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public Long apply(ReservationApplyRequest request) {

        return 0L;
    }

    @Override
    public Boolean cancel(Long id) {

        return false;
    }

    @Override
    public ReservationVO getById(Long id) {
        Reservation reservation = reservationMapper.selectById(id);

        if (reservation == null) {
            return null;
        }

        return convertToVO(reservation);
    }

    @Override
    public IPage<ReservationVO> getByUserId(Long userId, PageQueryRequest request) {
        Page<Reservation> page = new Page<>(
                request.getPageNumber(),
                request.getPageSize()
        );

        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreatedAt);

        IPage<Reservation> result = reservationMapper.selectPage(page, wrapper);

        return result.convert(this::convertToVO);
    }

    private ReservationVO convertToVO(Reservation reservation) {
        return ReservationVO.builder()
                .id(reservation.getId())
                .venueId(reservation.getVenueId())
                .resourceId(reservation.getResourceId())
                .slotDate(reservation.getSlotDate())
                .startUnit(reservation.getStartUnit())
                .endUnit(reservation.getEndUnit())
                .size(reservation.getSize())
                .status(reservation.getStatus())
                .build();
    }
}
