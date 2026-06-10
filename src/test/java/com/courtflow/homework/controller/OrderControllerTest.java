package com.courtflow.homework.controller;

import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.utils.BusinessIdGenerator;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.PaymentMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.OrderWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.courtflow.homework.common.enums.ReservationStatusEnum.QUEUING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private VenueResourceMapper venueResourceMapper;

    @Mock
    private OrderWorkflowService orderWorkflowService;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private OrderController orderController;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectCreatingOrderForQueueingReservation() {
        UserContext.set(1L, "USER");
        when(reservationMapper.selectById(8L)).thenReturn(Reservation.builder()
                .id(8L)
                .userId(1L)
                .status(QUEUING)
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderController.create(Map.of("reservationId", 8L)));

        assertEquals("预约正在处理中，请等待占位完成后再创建订单。", exception.getMessage());
        verify(orderMapper, never()).insert(any(com.courtflow.homework.entity.Order.class));
    }
}
