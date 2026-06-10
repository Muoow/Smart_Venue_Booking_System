package com.courtflow.homework.service.Impl;

import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.OrderStatusEnum;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.utils.BusinessIdGenerator;
import com.courtflow.homework.entity.Order;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.PaymentMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.UserMapper;
import com.courtflow.homework.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private ReservationService reservationService;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private OrderWorkflowServiceImpl orderWorkflowService;

    @Test
    void shouldRejectPayingQueueingReservation() {
        Order order = Order.builder()
                .id(11L)
                .userId(1L)
                .status(OrderStatusEnum.UNPAID)
                .expiredAt(new Date(System.currentTimeMillis() + 60_000L))
                .build();
        Reservation reservation = Reservation.builder()
                .id(21L)
                .orderId(11L)
                .status(ReservationStatusEnum.QUEUING)
                .build();

        when(orderMapper.selectById(11L)).thenReturn(order);
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderWorkflowService.payOrder(11L, 1L, 1));

        assertEquals(ResultCode.CONFLICT, exception.getResultCode());
        verify(paymentMapper, never()).insert(any(com.courtflow.homework.entity.Payment.class));
    }

    @Test
    void shouldCloseOrderWhenLinkedReservationExpiredBeforePayment() {
        Order order = Order.builder()
                .id(12L)
                .userId(1L)
                .status(OrderStatusEnum.UNPAID)
                .expiredAt(new Date(System.currentTimeMillis() + 60_000L))
                .build();
        Reservation reservation = Reservation.builder()
                .id(22L)
                .orderId(12L)
                .status(ReservationStatusEnum.EXPIRED)
                .build();

        when(orderMapper.selectById(12L)).thenReturn(order);
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));
        when(paymentMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderWorkflowService.payOrder(12L, 1L, 1));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verify(orderMapper).updateById(any(Order.class));
        verify(paymentMapper, never()).insert(any(com.courtflow.homework.entity.Payment.class));
    }
}
