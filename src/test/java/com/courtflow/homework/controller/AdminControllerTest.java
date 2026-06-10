package com.courtflow.homework.controller;

import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.User;
import com.courtflow.homework.entity.Venue;
import com.courtflow.homework.entity.VenueAdmin;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.PaymentMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.UserMapper;
import com.courtflow.homework.mapping.VenueAdminMapper;
import com.courtflow.homework.mapping.VenueMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.OrderWorkflowService;
import com.courtflow.homework.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private VenueMapper venueMapper;

    @Mock
    private VenueResourceMapper venueResourceMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private VenueAdminMapper venueAdminMapper;

    @Mock
    private ReservationService reservationService;

    @Mock
    private OrderWorkflowService orderWorkflowService;

    @InjectMocks
    private AdminController adminController;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldFilterVenuesForVenueAdmin() {
        UserContext.set(4L, "VENUE_ADMIN");
        when(venueAdminMapper.selectList(any())).thenReturn(List.of(new VenueAdmin(1L, 4L, 1L)));
        when(venueMapper.selectAllForAdmin()).thenReturn(List.of(
                Map.of("venueId", 1L, "venueName", "主体育馆", "venueType", "羽毛球", "venueStatus", 1),
                Map.of("venueId", 2L, "venueName", "中央篮球馆", "venueType", "篮球", "venueStatus", 1)
        ));
        when(venueResourceMapper.selectAllForAdmin()).thenReturn(List.of(
                Map.of("resourceId", 1L, "venueId", 1L, "resourceName", "A1", "resourceTypeCode", 1, "capacity", 4, "price", 40, "unitMinutes", 10, "resourceStatus", 1),
                Map.of("resourceId", 2L, "venueId", 2L, "resourceName", "B1", "resourceTypeCode", 2, "capacity", 6, "price", 60, "unitMinutes", 20, "resourceStatus", 1)
        ));

        List<Map<String, Object>> venues = adminController.venues().getData();

        assertEquals(1, venues.size());
        assertEquals(1L, venues.get(0).get("id"));
        assertEquals("主体育馆", venues.get(0).get("name"));
    }

    @Test
    void shouldFilterReservationsForVenueAdmin() {
        UserContext.set(4L, "VENUE_ADMIN");
        when(venueAdminMapper.selectList(any())).thenReturn(List.of(new VenueAdmin(1L, 4L, 1L)));
        when(userMapper.selectList(any())).thenReturn(List.of(
                User.builder().id(11L).username("u1").build(),
                User.builder().id(12L).username("u2").build()
        ));
        when(venueMapper.selectList(any())).thenReturn(List.of(
                Venue.builder().id(1L).name("主体育馆").build(),
                Venue.builder().id(2L).name("中央篮球馆").build()
        ));
        when(venueResourceMapper.selectList(any())).thenReturn(List.of(
                VenueResource.builder().id(21L).venueId(1L).name("A1").unitMinutes(10).build(),
                VenueResource.builder().id(22L).venueId(2L).name("B1").unitMinutes(20).build()
        ));
        when(reservationMapper.selectList(any())).thenReturn(List.of(
                Reservation.builder().id(101L).userId(11L).venueId(1L).resourceId(21L).slotDate(new Date()).status(ReservationStatusEnum.RESERVED).createdAt(new Date()).build(),
                Reservation.builder().id(102L).userId(12L).venueId(2L).resourceId(22L).slotDate(new Date()).status(ReservationStatusEnum.RESERVED).createdAt(new Date()).build()
        ));

        Map<String, Object> data = adminController.reservations(1, 20, null, null, null).getData();
        List<?> records = (List<?>) data.get("records");

        assertEquals(1, records.size());
        assertEquals(1L, ((Map<?, ?>) records.get(0)).get("venueId"));
    }

    @Test
    void shouldRejectVenueAdminAccessingPayments() {
        UserContext.set(4L, "VENUE_ADMIN");

        BusinessException exception = assertThrows(BusinessException.class, () -> adminController.payments());

        assertEquals("仅超级管理员可访问该功能。", exception.getMessage());
        verifyNoInteractions(paymentMapper);
    }

    @Test
    void shouldAllowVenueAdminUpdatingVenueStatusOnly() {
        UserContext.set(4L, "VENUE_ADMIN");
        when(venueAdminMapper.selectList(any())).thenReturn(List.of(new VenueAdmin(1L, 4L, 1L)));
        when(venueMapper.selectById(1L)).thenReturn(Venue.builder().id(1L).name("主体育馆").type("羽毛球").status(1).build());

        Map<String, Object> data = adminController.updateVenueStatus(1L, Map.of("status", 0)).getData();

        assertEquals(0, data.get("status"));
        verify(venueMapper).updateById(any(Venue.class));
    }

    @Test
    void shouldRejectVenueAdminUpdatingVenueDetails() {
        UserContext.set(4L, "VENUE_ADMIN");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminController.updateVenue(1L, Map.of("name", "新名称", "type", "新类型", "status", 1)));

        assertEquals("仅超级管理员可访问该功能。", exception.getMessage());
    }
}
