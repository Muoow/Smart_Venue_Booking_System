package com.courtflow.homework.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.courtflow.homework.common.constant.MqConstant;
import com.courtflow.homework.common.dto.request.PageQueryRequest;
import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.OrderStatusEnum;
import com.courtflow.homework.common.enums.ResourceStatusEnum;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.enums.TimeSlotStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.vo.ReservationVO;
import com.courtflow.homework.entity.Order;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.TimeSlot;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.TimeSlotMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.ReservationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ReservationServiceImpl implements ReservationService {

    private static final ConcurrentMap<String, ReentrantLock> LOCAL_SLOT_LOCKS = new ConcurrentHashMap<>();
    private static final int MAX_ADVANCE_BOOKING_DAYS = 14;

    private final ReservationMapper reservationMapper;

    private final TimeSlotMapper timeSlotMapper;

    private final OrderMapper orderMapper;

    private final VenueResourceMapper venueResourceMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Value("${courtflow.demo.enabled:false}")
    private boolean demoEnabled;

    @Autowired
    public ReservationServiceImpl(
            ReservationMapper reservationMapper,
            TimeSlotMapper timeSlotMapper,
            OrderMapper orderMapper,
            VenueResourceMapper venueResourceMapper
    ) {
        this.reservationMapper = reservationMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.orderMapper = orderMapper;
        this.venueResourceMapper = venueResourceMapper;
    }

    @Override
    @Transactional
    public Long apply(ReservationApplyRequest request) {
        validateApplyRequest(request);
        VenueResource resource = requireResource(request.getResourceId());
        Date normalizedSlotDate = normalizeSlotDate(request.getSlotDate());
        request.setSlotDate(normalizedSlotDate);

        validateBookingDateRange(normalizedSlotDate);
        validateSlotRange(resource, request.getStartUnit(), request.getEndUnit());
        validateBookingStartTime(normalizedSlotDate, request.getStartUnit(), resource.getUnitMinutes());
        validateReservationSize(resource, request.getSize());

        Reservation reservation = Reservation.builder()
                .userId(request.getUserId())
                .venueId(request.getVenueId())
                .resourceId(request.getResourceId())
                .slotDate(normalizedSlotDate)
                .startUnit(request.getStartUnit())
                .endUnit(request.getEndUnit())
                .size(request.getSize())
                .status(ReservationStatusEnum.QUEUING)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        reservationMapper.insert(reservation);

        if (demoEnabled || redisTemplate == null || rabbitTemplate == null) {
            reserveLocally(reservation, resource);
            return reservation.getId();
        }

        String inventoryKey = buildInventoryKey(request);
        Long reservedCount = redisTemplate.opsForValue().increment(inventoryKey, request.getSize());
        if (reservedCount == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "Redis inventory reservation failed.");
        }

        int partition = resolvePartition(request.getResourceId());
        rabbitTemplate.convertAndSend(
                MqConstant.RESERVATION_EXCHANGE,
                MqConstant.RESERVATION_ROUTING_KEY + partition,
                reservation.getId()
        );

        return reservation.getId();
    }

    @Override
    @Transactional
    public Boolean cancel(Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Reservation not found.");
        }
        if (ReservationStatusEnum.CANCELLED.equals(reservation.getStatus())) {
            return true;
        }
        if (ReservationStatusEnum.FINISHED.equals(reservation.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已完成预约不能取消。");
        }
        if (ReservationStatusEnum.CHECKED_IN.equals(reservation.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已签到预约不能取消，请先完成使用或联系管理员处理。");
        }
        if (ReservationStatusEnum.EXPIRED.equals(reservation.getStatus())) {
            return true;
        }

        if (reservation.getOrderId() != null) {
            Order order = orderMapper.selectById(reservation.getOrderId());
            if (order != null) {
                if (order.getStatus() == OrderStatusEnum.UNPAID) {
                    order.setStatus(OrderStatusEnum.CLOSED);
                    order.setUpdatedAt(new Date());
                    orderMapper.updateById(order);
                }
            }
        }

        reservation.setStatus(ReservationStatusEnum.CANCELLED);
        reservation.setUpdatedAt(new Date());
        reservationMapper.updateById(reservation);

        releaseLocalSlots(reservation);

        if (demoEnabled || redisTemplate == null) {
            return true;
        }

        String inventoryKey = buildInventoryKey(
                reservation.getResourceId(),
                reservation.getSlotDate(),
                reservation.getStartUnit(),
                reservation.getEndUnit()
        );
        redisTemplate.opsForValue().decrement(inventoryKey, reservation.getSize());
        return true;
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

    @Override
    public Map<String, Object> getAvailability(Long resourceId, Date slotDate) {
        VenueResource resource = requireResource(resourceId);
        Date normalizedSlotDate = normalizeSlotDate(slotDate);
        validateBookingDateRange(normalizedSlotDate);
        int totalSlots = totalSlots(resource);
        int capacity = safeCapacity(resource);

        Map<Integer, TimeSlot> slotMap = timeSlotMapper.selectList(
                Wrappers.<TimeSlot>lambdaQuery()
                        .eq(TimeSlot::getResourceId, resourceId)
                        .eq(TimeSlot::getSlotDate, normalizedSlotDate)
        ).stream().collect(LinkedHashMap::new, (map, item) -> map.put(item.getSlotUnit(), item), LinkedHashMap::putAll);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int unit = 0; unit < totalSlots; unit++) {
            TimeSlot slot = slotMap.get(unit);
            int bookedCount = slot == null || slot.getBookedCount() == null ? 0 : slot.getBookedCount();
            TimeSlotStatusEnum status = slot == null || slot.getStatus() == null
                    ? (bookedCount >= capacity ? TimeSlotStatusEnum.FULL : TimeSlotStatusEnum.FREE)
                    : slot.getStatus();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slotUnit", unit);
            row.put("startTime", formatUnitText(unit, resource.getUnitMinutes()));
            row.put("endTime", formatUnitText(unit + 1, resource.getUnitMinutes()));
            row.put("bookedCount", bookedCount);
            row.put("remainingCapacity", Math.max(capacity - bookedCount, 0));
            row.put("status", status.getValue());
            row.put("statusLabel", timeSlotStatusLabel(status));
            slots.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("slotDate", normalizedSlotDate);
        result.put("unitMinutes", resource.getUnitMinutes());
        result.put("capacity", capacity);
        result.put("slots", slots);
        return result;
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

    private void reserveLocally(Reservation reservation, VenueResource resource) {
        String lockKey = buildLocalLockKey(reservation.getResourceId(), reservation.getSlotDate());
        ReentrantLock lock = LOCAL_SLOT_LOCKS.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        try {
            List<TimeSlot> slots = loadOrCreateSlots(resource, reservation.getSlotDate(), reservation.getStartUnit(), reservation.getEndUnit());
            int capacity = safeCapacity(resource);

            for (TimeSlot slot : slots) {
                if (slot.getStatus() == TimeSlotStatusEnum.BLOCKED) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "当前时段不可预约。");
                }
                int currentBooked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
                if (currentBooked + reservation.getSize() > capacity) {
                    throw new BusinessException(ResultCode.CONFLICT, "预约人数超出当前时段容量。");
                }
            }

            for (TimeSlot slot : slots) {
                int currentBooked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
                int nextBooked = currentBooked + reservation.getSize();
                TimeSlotStatusEnum nextStatus = nextBooked >= capacity ? TimeSlotStatusEnum.FULL : TimeSlotStatusEnum.FREE;
                int updated = timeSlotMapper.update(null,
                        Wrappers.<TimeSlot>lambdaUpdate()
                                .eq(TimeSlot::getId, slot.getId())
                                .eq(TimeSlot::getBookedCount, currentBooked)
                                .set(TimeSlot::getBookedCount, nextBooked)
                                .set(TimeSlot::getStatus, nextStatus)
                                .set(TimeSlot::getUpdatedAt, new Date())
                );
                if (updated != 1) {
                    throw new BusinessException(ResultCode.CONFLICT, "预约时段发生变化，请刷新后重试。");
                }
            }

            reservation.setStatus(ReservationStatusEnum.RESERVED);
            reservation.setUpdatedAt(new Date());
            reservationMapper.updateById(reservation);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                LOCAL_SLOT_LOCKS.remove(lockKey, lock);
            }
        }
    }

    private void releaseLocalSlots(Reservation reservation) {
        String lockKey = buildLocalLockKey(reservation.getResourceId(), reservation.getSlotDate());
        ReentrantLock lock = LOCAL_SLOT_LOCKS.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        try {
            VenueResource resource = venueResourceMapper.selectById(reservation.getResourceId());
            if (resource == null) {
                return;
            }
            int capacity = safeCapacity(resource);
            List<TimeSlot> slots = timeSlotMapper.selectList(
                    Wrappers.<TimeSlot>lambdaQuery()
                            .eq(TimeSlot::getResourceId, reservation.getResourceId())
                            .eq(TimeSlot::getSlotDate, reservation.getSlotDate())
                            .ge(TimeSlot::getSlotUnit, reservation.getStartUnit())
                            .le(TimeSlot::getSlotUnit, reservation.getEndUnit())
            );
            for (TimeSlot slot : slots) {
                int currentBooked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
                int nextBooked = Math.max(currentBooked - reservation.getSize(), 0);
                TimeSlotStatusEnum nextStatus = slot.getStatus() == TimeSlotStatusEnum.BLOCKED
                        ? TimeSlotStatusEnum.BLOCKED
                        : (nextBooked >= capacity ? TimeSlotStatusEnum.FULL : TimeSlotStatusEnum.FREE);
                timeSlotMapper.update(null,
                        Wrappers.<TimeSlot>lambdaUpdate()
                                .eq(TimeSlot::getId, slot.getId())
                                .set(TimeSlot::getBookedCount, nextBooked)
                                .set(TimeSlot::getStatus, nextStatus)
                                .set(TimeSlot::getUpdatedAt, new Date())
                );
            }
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                LOCAL_SLOT_LOCKS.remove(lockKey, lock);
            }
        }
    }

    private List<TimeSlot> loadOrCreateSlots(VenueResource resource, Date slotDate, Integer startUnit, Integer endUnit) {
        List<TimeSlot> slots = new ArrayList<>();
        for (int unit = startUnit; unit <= endUnit; unit++) {
            TimeSlot slot = timeSlotMapper.selectOne(
                    Wrappers.<TimeSlot>lambdaQuery()
                            .eq(TimeSlot::getResourceId, resource.getId())
                            .eq(TimeSlot::getSlotDate, slotDate)
                            .eq(TimeSlot::getSlotUnit, unit)
            );
            if (slot == null) {
                slot = TimeSlot.builder()
                        .resourceId(resource.getId())
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
                                    .eq(TimeSlot::getResourceId, resource.getId())
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

    private void validateApplyRequest(ReservationApplyRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Request must not be null.");
        }
        if (request.getUserId() == null || request.getVenueId() == null || request.getResourceId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "User, venue and resource are required.");
        }
        if (request.getSlotDate() == null || request.getStartUnit() == null || request.getEndUnit() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Slot range is required.");
        }
        if (request.getEndUnit() < request.getStartUnit()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "End unit must be greater than start unit.");
        }
        if (request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Reservation size must be positive.");
        }
    }

    private VenueResource requireResource(Long resourceId) {
        VenueResource resource = venueResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约资源不存在。");
        }
        if (resource.getStatus() != ResourceStatusEnum.ENABLED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前资源不可预约。");
        }
        return resource;
    }

    private void validateSlotRange(VenueResource resource, Integer startUnit, Integer endUnit) {
        int totalSlots = totalSlots(resource);
        if (startUnit == null || endUnit == null || startUnit < 0 || endUnit >= totalSlots) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约时段超出资源可用范围。");
        }
    }

    private void validateReservationSize(VenueResource resource, Integer size) {
        if (size == null || size <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约人数必须大于 0。");
        }
        if (size > safeCapacity(resource)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约人数超出资源容量。");
        }
    }

    private int resolvePartition(Long resourceId) {
        return Math.floorMod(resourceId.intValue(), MqConstant.QUEUE_PARTITIONS);
    }

    private String buildInventoryKey(ReservationApplyRequest request) {
        return buildInventoryKey(
                request.getResourceId(),
                request.getSlotDate(),
                request.getStartUnit(),
                request.getEndUnit()
        );
    }

    private String buildInventoryKey(Long resourceId, Date slotDate, Integer startUnit, Integer endUnit) {
        return "courtflow:inventory:" + resourceId + ":" + slotDate.getTime() + ":" + startUnit + ":" + endUnit;
    }

    private String buildLocalLockKey(Long resourceId, Date slotDate) {
        return resourceId + ":" + normalizeSlotDate(slotDate).getTime();
    }

    private Date normalizeSlotDate(Date slotDate) {
        if (slotDate == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(slotDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private void validateBookingDateRange(Date slotDate) {
        if (slotDate == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约日期不能为空。");
        }
        Date today = normalizeSlotDate(new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.add(Calendar.DAY_OF_YEAR, MAX_ADVANCE_BOOKING_DAYS);
        Date maxDate = calendar.getTime();

        if (slotDate.before(today)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约日期不能早于今天。");
        }
        if (slotDate.after(maxDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持预约14天以内的场馆！");
        }
    }

    private void validateBookingStartTime(Date slotDate, Integer startUnit, Integer unitMinutes) {
        if (slotDate == null || startUnit == null) {
            return;
        }
        Date today = normalizeSlotDate(new Date());
        if (!slotDate.equals(today)) {
            return;
        }

        int minutesPerUnit = unitMinutes == null || unitMinutes <= 0 ? 10 : unitMinutes;
        int slotStartMinutes = startUnit * minutesPerUnit;
        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (slotStartMinutes < currentMinutes) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前时刻之前的时段不可预约。");
        }
    }

    private int totalSlots(VenueResource resource) {
        int unitMinutes = resource.getUnitMinutes() == null || resource.getUnitMinutes() <= 0 ? 10 : resource.getUnitMinutes();
        return 24 * 60 / unitMinutes;
    }

    private int safeCapacity(VenueResource resource) {
        return resource.getCapacity() == null || resource.getCapacity() <= 0 ? 1 : resource.getCapacity();
    }

    private String formatUnitText(int unit, Integer unitMinutes) {
        int minutesPerUnit = unitMinutes == null || unitMinutes <= 0 ? 10 : unitMinutes;
        int totalMinutes = unit * minutesPerUnit;
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private String timeSlotStatusLabel(TimeSlotStatusEnum status) {
        return switch (status) {
            case FREE -> "可预约";
            case FULL -> "已满";
            case BLOCKED -> "不可用";
        };
    }
}
