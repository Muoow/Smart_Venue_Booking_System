package com.courtflow.homework.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.enums.TimeSlotStatusEnum;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.TimeSlot;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.TimeSlotMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class ReservationHandler {

    private final ReservationMapper reservationMapper;

    private final VenueResourceMapper venueResourceMapper;

    private final TimeSlotMapper timeSlotMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    public ReservationHandler(ReservationMapper reservationMapper, VenueResourceMapper venueResourceMapper, TimeSlotMapper timeSlotMapper) {
        this.reservationMapper = reservationMapper;
        this.venueResourceMapper = venueResourceMapper;
        this.timeSlotMapper = timeSlotMapper;
    }

    @Transactional
    public void process(Long reservationId) {

        Reservation reservation = reservationMapper.selectById(reservationId);

        if (reservation == null || !ReservationStatusEnum.QUEUING.equals(reservation.getStatus())) {
            return;
        }

        Long resourceId = reservation.getResourceId();
        Date slotDate = reservation.getSlotDate();
        Integer startUnit = reservation.getStartUnit();
        Integer endUnit = reservation.getEndUnit();

        VenueResource resource = venueResourceMapper.selectById(resourceId);
        if (resource == null) return;

        int unitMinutes = resource.getUnitMinutes();
        int totalSlots = 24 * 60 / unitMinutes;

        List<TimeSlot> slots = new ArrayList<>();
        for (int unit = startUnit; unit <= endUnit; unit++) {

            if (unit >= totalSlots) break;

            TimeSlot slot = timeSlotMapper.selectOne(
                    Wrappers.<TimeSlot>lambdaQuery()
                            .eq(TimeSlot::getResourceId, resourceId)
                            .eq(TimeSlot::getSlotDate, slotDate)
                            .eq(TimeSlot::getSlotUnit, unit)
            );

            if (slot == null) {
                slot = TimeSlot.builder()
                        .resourceId(resourceId)
                        .slotDate(slotDate)
                        .slotUnit(unit)
                        .status(TimeSlotStatusEnum.FREE)
                        .bookedCount(0)
                        .build();
                timeSlotMapper.insert(slot);
            }
            slots.add(slot);
        }

        if (slots.size() != endUnit - startUnit + 1) {
            return;
        }

        for (TimeSlot slot : slots) {
            int updated = timeSlotMapper.update(null,
                    Wrappers.<TimeSlot>lambdaUpdate()
                            .eq(TimeSlot::getId, slot.getId())
                            .eq(TimeSlot::getBookedCount, slot.getBookedCount())
                            .set(TimeSlot::getBookedCount, slot.getBookedCount() + reservation.getSize())
            );
            if (updated != 1) {
                throw new RuntimeException("Inventory update failed.");
            }
        }

        reservation.setStatus(ReservationStatusEnum.FINISHED);
        reservationMapper.updateById(reservation);
    }
}
