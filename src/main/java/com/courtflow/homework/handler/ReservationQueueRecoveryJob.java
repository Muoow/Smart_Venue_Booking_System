package com.courtflow.homework.handler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.utils.ReservationMessagePublisher;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.mapping.ReservationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@ConditionalOnProperty(name = "courtflow.middleware.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ReservationQueueRecoveryJob {

    private static final long STALE_QUEUEING_THRESHOLD_MS = 45_000L;
    private static final int RECOVERY_BATCH_SIZE = 50;

    private final ReservationMapper reservationMapper;
    private final ReservationMessagePublisher reservationMessagePublisher;

    public ReservationQueueRecoveryJob(ReservationMapper reservationMapper,
                                       ReservationMessagePublisher reservationMessagePublisher) {
        this.reservationMapper = reservationMapper;
        this.reservationMessagePublisher = reservationMessagePublisher;
    }

    @Scheduled(initialDelay = 60000L, fixedDelay = 30000L)
    public void recoverStaleQueueingReservations() {
        Date cutoff = new Date(System.currentTimeMillis() - STALE_QUEUEING_THRESHOLD_MS);
        Page<Reservation> page = new Page<>(1, RECOVERY_BATCH_SIZE);
        List<Reservation> reservations = reservationMapper.selectPage(
                page,
                Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getStatus, ReservationStatusEnum.QUEUING)
                        .le(Reservation::getUpdatedAt, cutoff)
                        .orderByAsc(Reservation::getUpdatedAt)
        ).getRecords();

        for (Reservation reservation : reservations) {
            Date now = new Date();
            int updated = reservationMapper.update(null,
                    Wrappers.<Reservation>lambdaUpdate()
                            .eq(Reservation::getId, reservation.getId())
                            .eq(Reservation::getStatus, ReservationStatusEnum.QUEUING)
                            .le(Reservation::getUpdatedAt, cutoff)
                            .set(Reservation::getUpdatedAt, now)
            );
            if (updated != 1) {
                continue;
            }

            boolean published = reservationMessagePublisher.publish(reservation.getId(), reservation.getResourceId());
            if (!published) {
                log.warn("Failed to republish stale queueing reservation. reservationId={}", reservation.getId());
            }
        }
    }
}
