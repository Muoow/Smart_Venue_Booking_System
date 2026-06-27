package com.courtflow.homework.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.enums.TimeSlotStatusEnum;
import com.courtflow.homework.common.utils.ReservationMessagePublisher;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.TimeSlot;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.TimeSlotMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ReservationHandler {

    private static final DefaultRedisScript<Long> RELEASE_INVENTORY_SCRIPT = buildReleaseInventoryScript();
    private static final String RETRY_KEY_PREFIX = "courtflow:reservation:retry:";
    private static final long RETRY_KEY_TTL_SECONDS = 1800L;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final StringRedisSerializer REDIS_SCRIPT_ARG_SERIALIZER = new StringRedisSerializer();
    private static final GenericToStringSerializer<Long> REDIS_SCRIPT_RESULT_SERIALIZER =
            new GenericToStringSerializer<>(Long.class);

    private final ReservationMapper reservationMapper;

    private final VenueResourceMapper venueResourceMapper;

    private final TimeSlotMapper timeSlotMapper;

    private final ReservationMessagePublisher reservationMessagePublisher;

    private final TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${courtflow.demo.enabled:false}")
    private boolean demoEnabled;

    public ReservationHandler(
            ReservationMapper reservationMapper,
            VenueResourceMapper venueResourceMapper,
            TimeSlotMapper timeSlotMapper,
            ReservationMessagePublisher reservationMessagePublisher,
            PlatformTransactionManager transactionManager
    ) {
        this.reservationMapper = reservationMapper;
        this.venueResourceMapper = venueResourceMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.reservationMessagePublisher = reservationMessagePublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void process(Long reservationId) {
        ProcessOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> processInTransaction(reservationId, status));
        } catch (RuntimeException ex) {
            log.error("Reservation consume failed unexpectedly, will retry. reservationId={}", reservationId, ex);
            retryOrExpireReservation(reservationId, "unexpected-consume-exception");
            return;
        }

        if (outcome == null || outcome == ProcessOutcome.SUCCESS) {
            clearRetryState(reservationId);
            return;
        }

        if (outcome == ProcessOutcome.RETRY) {
            retryOrExpireReservation(reservationId, "slot-update-conflict");
            return;
        }

        expireReservation(reservationId);
    }

    private ProcessOutcome processInTransaction(Long reservationId, TransactionStatus transactionStatus) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !ReservationStatusEnum.QUEUING.equals(reservation.getStatus())) {
            return ProcessOutcome.SUCCESS;
        }

        Long resourceId = reservation.getResourceId();
        Date slotDate = reservation.getSlotDate();
        Integer startUnit = reservation.getStartUnit();
        Integer endUnit = reservation.getEndUnit();

        VenueResource resource = venueResourceMapper.selectById(resourceId);
        if (resource == null) {
            return ProcessOutcome.EXPIRE;
        }

        int unitMinutes = resource.getUnitMinutes() == null || resource.getUnitMinutes() <= 0 ? 10 : resource.getUnitMinutes();
        int totalSlots = 24 * 60 / unitMinutes;
        int capacity = resource.getCapacity() == null || resource.getCapacity() <= 0 ? 1 : resource.getCapacity();

        List<TimeSlot> slots = loadOrCreateSlots(resourceId, slotDate, startUnit, endUnit, totalSlots);
        if (slots.size() != endUnit - startUnit + 1) {
            return ProcessOutcome.EXPIRE;
        }

        for (TimeSlot slot : slots) {
            if (slot.getStatus() == TimeSlotStatusEnum.BLOCKED) {
                return ProcessOutcome.EXPIRE;
            }
            int currentBooked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
            if (currentBooked + reservation.getSize() > capacity) {
                return ProcessOutcome.EXPIRE;
            }
        }

        for (TimeSlot slot : slots) {
            int currentBooked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
            int nextBooked = currentBooked + reservation.getSize();
            int updated = timeSlotMapper.update(null,
                    Wrappers.<TimeSlot>lambdaUpdate()
                            .eq(TimeSlot::getId, slot.getId())
                            .eq(TimeSlot::getBookedCount, currentBooked)
                            .set(TimeSlot::getBookedCount, nextBooked)
                            .set(TimeSlot::getStatus, nextBooked >= capacity ? TimeSlotStatusEnum.FULL : TimeSlotStatusEnum.FREE)
                            .set(TimeSlot::getUpdatedAt, new Date())
            );
            if (updated != 1) {
                transactionStatus.setRollbackOnly();
                return ProcessOutcome.RETRY;
            }
        }

        reservation.setStatus(ReservationStatusEnum.RESERVED);
        reservation.setUpdatedAt(new Date());
        reservationMapper.updateById(reservation);
        return ProcessOutcome.SUCCESS;
    }

    private List<TimeSlot> loadOrCreateSlots(Long resourceId, Date slotDate, Integer startUnit, Integer endUnit, int totalSlots) {
        List<TimeSlot> slots = new ArrayList<>();
        for (int unit = startUnit; unit <= endUnit; unit++) {
            if (unit >= totalSlots) {
                break;
            }
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
                        .createdAt(new Date())
                        .updatedAt(new Date())
                        .build();
                try {
                    timeSlotMapper.insert(slot);
                } catch (RuntimeException ex) {
                    slot = timeSlotMapper.selectOne(
                            Wrappers.<TimeSlot>lambdaQuery()
                                    .eq(TimeSlot::getResourceId, resourceId)
                                    .eq(TimeSlot::getSlotDate, slotDate)
                                    .eq(TimeSlot::getSlotUnit, unit)
                    );
                    if (slot == null) {
                        throw ex;
                    }
                }
            }
            slots.add(slot);
        }
        return slots;
    }

    private void retryOrExpireReservation(Long reservationId, String reason) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || reservation.getStatus() != ReservationStatusEnum.QUEUING) {
            clearRetryState(reservationId);
            return;
        }

        long retryAttempt = incrementRetryAttempt(reservationId);
        if (retryAttempt <= MAX_RETRY_ATTEMPTS && requeueReservation(reservation, retryAttempt)) {
            log.warn("Reservation requeued for retry. reservationId={}, attempt={}, reason={}",
                    reservationId, retryAttempt, reason);
            return;
        }

        log.error("Reservation exceeded retry limit or requeue failed, expiring reservation. reservationId={}, attempts={}, reason={}",
                reservationId, retryAttempt, reason);
        expireReservation(reservationId);
    }

    private boolean requeueReservation(Reservation reservation, long retryAttempt) {
        reservation.setUpdatedAt(new Date());
        reservationMapper.update(null,
                Wrappers.<Reservation>lambdaUpdate()
                        .eq(Reservation::getId, reservation.getId())
                        .eq(Reservation::getStatus, ReservationStatusEnum.QUEUING)
                        .set(Reservation::getUpdatedAt, reservation.getUpdatedAt())
        );
        return reservationMessagePublisher.publish(reservation.getId(), reservation.getResourceId());
    }

    private void expireReservation(Long reservationId) {
        transactionTemplate.executeWithoutResult(status -> {
            Reservation reservation = reservationMapper.selectById(reservationId);
            if (reservation == null || reservation.getStatus() != ReservationStatusEnum.QUEUING) {
                clearRetryState(reservationId);
                return;
            }

            reservation.setStatus(ReservationStatusEnum.EXPIRED);
            reservation.setUpdatedAt(new Date());
            reservationMapper.updateById(reservation);
            try {
                releaseRemoteInventory(reservation);
            } catch (RuntimeException ex) {
                log.error("Failed to release remote inventory while expiring reservation. reservationId={}", reservationId, ex);
            }
            clearRetryState(reservationId);
        });
    }

    private long incrementRetryAttempt(Long reservationId) {
        if (redisTemplate == null || reservationId == null) {
            return MAX_RETRY_ATTEMPTS + 1L;
        }
        String retryKey = buildRetryKey(reservationId);
        Long current = redisTemplate.opsForValue().increment(retryKey);
        redisTemplate.expire(retryKey, RETRY_KEY_TTL_SECONDS, TimeUnit.SECONDS);
        return current == null ? MAX_RETRY_ATTEMPTS + 1L : current;
    }

    private void clearRetryState(Long reservationId) {
        if (redisTemplate == null || reservationId == null) {
            return;
        }
        redisTemplate.delete(buildRetryKey(reservationId));
    }

    private String buildRetryKey(Long reservationId) {
        return RETRY_KEY_PREFIX + reservationId;
    }

    private void releaseRemoteInventory(Reservation reservation) {
        if (demoEnabled || redisTemplate == null || reservation == null) {
            return;
        }
        List<String> keys = buildInventoryKeys(
                reservation.getResourceId(),
                reservation.getSlotDate(),
                reservation.getStartUnit(),
                reservation.getEndUnit()
        );
        redisTemplate.execute(
                RELEASE_INVENTORY_SCRIPT,
                REDIS_SCRIPT_ARG_SERIALIZER,
                REDIS_SCRIPT_RESULT_SERIALIZER,
                new ArrayList<>(keys),
                String.valueOf(Math.max(reservation.getSize() == null ? 0 : reservation.getSize(), 0)),
                String.valueOf(calculateInventoryTtlSeconds(reservation.getSlotDate()))
        );
    }

    private List<String> buildInventoryKeys(Long resourceId, Date slotDate, Integer startUnit, Integer endUnit) {
        List<String> keys = new ArrayList<>();
        if (resourceId == null || slotDate == null || startUnit == null || endUnit == null) {
            return keys;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(slotDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long normalizedDate = calendar.getTimeInMillis();
        for (int unit = startUnit; unit <= endUnit; unit++) {
            keys.add("courtflow:inventory:" + resourceId + ":" + normalizedDate + ":" + unit);
        }
        return keys;
    }

    private long calculateInventoryTtlSeconds(Date slotDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(slotDate == null ? new Date() : slotDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, 3);
        long ttlMs = calendar.getTimeInMillis() - System.currentTimeMillis();
        return Math.max(ttlMs / 1000L, 86400L);
    }

    private static DefaultRedisScript<Long> buildReleaseInventoryScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                local size = tonumber(ARGV[1])
                local ttl = tonumber(ARGV[2])
                for i = 1, #KEYS do
                    local current = tonumber(redis.call('GET', KEYS[i]) or '0')
                    local next = current - size
                    if next <= 0 then
                        redis.call('DEL', KEYS[i])
                    else
                        redis.call('SET', KEYS[i], next)
                        if ttl > 0 then
                            redis.call('EXPIRE', KEYS[i], ttl)
                        end
                    end
                end
                return 1
                """);
        return script;
    }

    private enum ProcessOutcome {
        SUCCESS,
        RETRY,
        EXPIRE
    }
}
