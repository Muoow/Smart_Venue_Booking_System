package com.courtflow.homework.controller;

import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationMapper reservationMapper;

    @InjectMocks
    private ReservationController reservationController;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectAccessingAnotherUsersReservation() {
        UserContext.set(1L, "USER");
        when(reservationMapper.selectById(9L)).thenReturn(Reservation.builder()
                .id(9L)
                .userId(2L)
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationController.getById(9L));

        assertEquals("无权访问该预约记录。", exception.getMessage());
        verifyNoInteractions(reservationService);
    }

    @Test
    void shouldAllowOwnerToCancelReservation() {
        UserContext.set(1L, "USER");
        when(reservationMapper.selectById(9L)).thenReturn(Reservation.builder()
                .id(9L)
                .userId(1L)
                .build());
        when(reservationService.cancel(9L)).thenReturn(true);

        reservationController.cancel(9L);

        verify(reservationService).cancel(9L);
    }
}
