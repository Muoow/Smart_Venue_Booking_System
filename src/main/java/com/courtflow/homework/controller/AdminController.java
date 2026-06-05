package com.courtflow.homework.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.OrderStatusEnum;
import com.courtflow.homework.common.enums.PaymentBizTypeEnum;
import com.courtflow.homework.common.enums.PaymentStatusEnum;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.enums.ResourceStatusEnum;
import com.courtflow.homework.common.enums.ResourceTypeEnum;
import com.courtflow.homework.common.enums.UserStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.entity.Order;
import com.courtflow.homework.entity.Payment;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.User;
import com.courtflow.homework.entity.Venue;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.PaymentMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.UserMapper;
import com.courtflow.homework.mapping.VenueMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.OrderWorkflowService;
import com.courtflow.homework.service.ReservationService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final VenueMapper venueMapper;

    private final VenueResourceMapper venueResourceMapper;

    private final ReservationMapper reservationMapper;

    private final UserMapper userMapper;

    private final OrderMapper orderMapper;

    private final PaymentMapper paymentMapper;

    private final ReservationService reservationService;

    private final OrderWorkflowService orderWorkflowService;

    public AdminController(
            VenueMapper venueMapper,
            VenueResourceMapper venueResourceMapper,
            ReservationMapper reservationMapper,
            UserMapper userMapper,
            OrderMapper orderMapper,
            PaymentMapper paymentMapper,
            ReservationService reservationService,
            OrderWorkflowService orderWorkflowService
    ) {
        this.venueMapper = venueMapper;
        this.venueResourceMapper = venueResourceMapper;
        this.reservationMapper = reservationMapper;
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.reservationService = reservationService;
        this.orderWorkflowService = orderWorkflowService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        assertAdmin();

        long venueCount = venueMapper.selectCount(Wrappers.<Venue>lambdaQuery());
        long enabledVenueCount = venueMapper.selectCount(
                Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1)
        );
        long resourceCount = venueResourceMapper.selectCount(Wrappers.<VenueResource>lambdaQuery());
        long enabledResourceCount = venueResourceMapper.selectCount(
                Wrappers.<VenueResource>lambdaQuery().eq(VenueResource::getStatus, ResourceStatusEnum.ENABLED)
        );
        long reservationCount = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery());
        long activeReservationCount = reservationMapper.selectCount(
                Wrappers.<Reservation>lambdaQuery().in(
                        Reservation::getStatus,
                        ReservationStatusEnum.QUEUING,
                        ReservationStatusEnum.RESERVED,
                        ReservationStatusEnum.CHECKED_IN
                )
        );
        long userCount = userMapper.selectCount(Wrappers.<User>lambdaQuery());
        long orderCount = orderMapper.selectCount(Wrappers.<Order>lambdaQuery());
        long paidOrderCount = orderMapper.selectCount(
                Wrappers.<Order>lambdaQuery().eq(Order::getStatus, OrderStatusEnum.PAID)
        );
        long pendingPaymentReviewCount = paymentMapper.selectCount(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getPayStatus, PaymentStatusEnum.PROCESSING)
        );

        List<Reservation> recentReservationsRaw = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()).stream()
                .sorted(Comparator.comparing(Reservation::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(8)
                .toList();
        List<Map<String, Object>> recentReservations = buildReservationRows(recentReservationsRaw);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("venueCount", venueCount);
        result.put("enabledVenueCount", enabledVenueCount);
        result.put("resourceCount", resourceCount);
        result.put("enabledResourceCount", enabledResourceCount);
        result.put("reservationCount", reservationCount);
        result.put("activeReservationCount", activeReservationCount);
        result.put("userCount", userCount);
        result.put("orderCount", orderCount);
        result.put("paidOrderCount", paidOrderCount);
        result.put("pendingPaymentReviewCount", pendingPaymentReviewCount);
        result.put("recentReservations", recentReservations);
        result.put("role", UserContext.getRole());
        return ApiResponse.success(result);
    }

    @GetMapping("/venues")
    public ApiResponse<List<Map<String, Object>>> venues() {
        assertAdmin();

        Map<Long, List<Map<String, Object>>> resourcesByVenue = venueResourceMapper.selectAllForAdmin().stream()
                .collect(Collectors.groupingBy(row -> readRowLong(row, "venueId", -1L), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> data = venueMapper.selectAllForAdmin().stream()
                .map(venue -> {
                    Long venueId = readRowLong(venue, "venueId", null);
                    List<Map<String, Object>> resources = resourcesByVenue.getOrDefault(venueId, List.of());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", venueId);
                    row.put("name", readRowText(venue, "venueName"));
                    row.put("type", readRowText(venue, "venueType"));
                    row.put("status", readRowInteger(venue, "venueStatus", 0));
                    row.put("resourceCount", resources.size());
                    row.put("createdAt", venue.get("venueCreatedAt"));
                    row.put("resources", resources.stream().map(this::toAdminResourceRow).toList());
                    return row;
                })
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/venues")
    public ApiResponse<Map<String, Object>> createVenue(@RequestBody Map<String, Object> payload) {
        assertAdmin();

        Venue venue = Venue.builder()
                .name(requireText(payload, "name", "场馆名称不能为空"))
                .type(requireText(payload, "type", "场馆类型不能为空"))
                .status(readInteger(payload, "status", 1))
                .createdAt(new Date())
                .build();
        venueMapper.insert(venue);
        return ApiResponse.success(toVenueRow(venue));
    }

    @PutMapping("/venues/{id}")
    public ApiResponse<Map<String, Object>> updateVenue(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        assertAdmin();

        Venue venue = requireVenue(id);
        venue.setName(requireText(payload, "name", "场馆名称不能为空"));
        venue.setType(requireText(payload, "type", "场馆类型不能为空"));
        venue.setStatus(readInteger(payload, "status", venue.getStatus()));
        venueMapper.updateById(venue);
        return ApiResponse.success(toVenueRow(venue));
    }

    @DeleteMapping("/venues/{id}")
    @Transactional
    public ApiResponse<Boolean> deleteVenue(@PathVariable Long id) {
        assertAdmin();
        requireVenue(id);

        long resourceCount = venueResourceMapper.selectCount(
                Wrappers.<VenueResource>lambdaQuery().eq(VenueResource::getVenueId, id)
        );
        if (resourceCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先删除该场馆下的资源。");
        }

        long reservationCount = reservationMapper.selectCount(
                Wrappers.<Reservation>lambdaQuery().eq(Reservation::getVenueId, id)
        );
        if (reservationCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该场馆已有预约记录，不能直接删除。");
        }

        venueMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    @GetMapping("/resources")
    public ApiResponse<List<Map<String, Object>>> resources() {
        assertAdmin();

        Map<Long, String> venueNameMap = venueMapper.selectAllForAdmin().stream()
                .collect(Collectors.toMap(
                        row -> readRowLong(row, "venueId", null),
                        row -> readRowText(row, "venueName"),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Map<String, Object>> data = venueResourceMapper.selectAllForAdmin().stream()
                .map(resource -> {
                    Long venueId = readRowLong(resource, "venueId", null);
                    Map<String, Object> row = toAdminResourceRow(resource);
                    row.put("venueName", venueNameMap.getOrDefault(venueId, "未关联场馆"));
                    return row;
                })
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/resources")
    public ApiResponse<Map<String, Object>> createResource(@RequestBody Map<String, Object> payload) {
        assertAdmin();

        Long venueId = requireLong(payload, "venueId", "请选择所属场馆");
        requireVenue(venueId);

        VenueResource resource = VenueResource.builder()
                .venueId(venueId)
                .name(requireText(payload, "name", "资源名称不能为空"))
                .resourceType(requireResourceType(readInteger(payload, "resourceType", null)))
                .capacity(readInteger(payload, "capacity", 0))
                .price(readInteger(payload, "price", 0))
                .unitMinutes(readInteger(payload, "unitMinutes", 10))
                .status(requireResourceStatus(readInteger(payload, "status", 1)))
                .build();
        venueResourceMapper.insert(resource);
        Map<String, Object> row = toResourceRow(resource);
        row.put("venueName", requireVenue(venueId).getName());
        return ApiResponse.success(row);
    }

    @PutMapping("/resources/{id}")
    public ApiResponse<Map<String, Object>> updateResource(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        assertAdmin();

        VenueResource resource = requireResource(id);
        Long venueId = requireLong(payload, "venueId", "请选择所属场馆");
        requireVenue(venueId);
        resource.setVenueId(venueId);
        resource.setName(requireText(payload, "name", "资源名称不能为空"));
        resource.setResourceType(requireResourceType(readInteger(payload, "resourceType", null)));
        resource.setCapacity(readInteger(payload, "capacity", 0));
        resource.setPrice(readInteger(payload, "price", 0));
        resource.setUnitMinutes(readInteger(payload, "unitMinutes", 10));
        resource.setStatus(requireResourceStatus(readInteger(payload, "status", 1)));
        venueResourceMapper.updateById(resource);

        Map<String, Object> row = toResourceRow(resource);
        row.put("venueName", requireVenue(venueId).getName());
        return ApiResponse.success(row);
    }

    @DeleteMapping("/resources/{id}")
    public ApiResponse<Boolean> deleteResource(@PathVariable Long id) {
        assertAdmin();
        requireResource(id);

        long reservationCount = reservationMapper.selectCount(
                Wrappers.<Reservation>lambdaQuery().eq(Reservation::getResourceId, id)
        );
        if (reservationCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该资源已有预约记录，不能直接删除。");
        }

        venueResourceMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    @GetMapping("/reservations")
    public ApiResponse<Map<String, Object>> reservations(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String slotDate,
            @RequestParam(required = false) String keyword
    ) {
        assertAdmin();

        Date filterDate = parseDateOnly(slotDate);
        String filterKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        Map<Long, String> userNameMap = userMapper.selectList(Wrappers.<User>lambdaQuery()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        Map<Long, String> venueNameMap = venueMapper.selectList(Wrappers.<Venue>lambdaQuery()).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName, (a, b) -> a));
        Map<Long, VenueResource> resourceMap = venueResourceMapper.selectList(Wrappers.<VenueResource>lambdaQuery()).stream()
                .collect(Collectors.toMap(VenueResource::getId, Function.identity(), (a, b) -> a));

        List<Reservation> filtered = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()).stream()
                .sorted(Comparator.comparing(Reservation::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .filter(item -> status == null || item.getStatus() == requireReservationStatus(status))
                .filter(item -> filterDate == null || sameDay(item.getSlotDate(), filterDate))
                .filter(item -> reservationMatchesKeyword(item, filterKeyword, userNameMap, venueNameMap, resourceMap))
                .toList();

        long total = filtered.size();
        int fromIndex = Math.min(Math.max((pageNumber - 1) * pageSize, 0), filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<Reservation> pageRecords = filtered.subList(fromIndex, toIndex);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", buildReservationRows(pageRecords));
        data.put("total", total);
        data.put("current", pageNumber);
        data.put("size", pageSize);
        data.put("pages", pageSize <= 0 ? 0 : (long) Math.ceil(total * 1.0 / pageSize));
        return ApiResponse.success(data);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ApiResponse<Boolean> cancelReservation(@PathVariable Long id) {
        assertAdmin();
        return ApiResponse.success(reservationService.cancel(id));
    }

    @PostMapping("/reservations/{id}/finish")
    public ApiResponse<Boolean> finishReservation(@PathVariable Long id) {
        assertAdmin();

        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约记录不存在。");
        }
        if (reservation.getStatus() == ReservationStatusEnum.FINISHED) {
            return ApiResponse.success(true);
        }
        if (reservation.getStatus() != ReservationStatusEnum.CHECKED_IN) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已签到预约可完结，请先完成到场签到。");
        }
        requirePaidReservationWithoutRefundProcessing(reservation);
        reservation.setStatus(ReservationStatusEnum.FINISHED);
        reservation.setUpdatedAt(new Date());
        reservationMapper.updateById(reservation);
        return ApiResponse.success(true);
    }

    @PostMapping("/reservations/{id}/check-in")
    public ApiResponse<Boolean> checkInReservation(@PathVariable Long id) {
        assertAdmin();

        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约记录不存在。");
        }
        if (reservation.getStatus() == ReservationStatusEnum.CHECKED_IN) {
            return ApiResponse.success(true);
        }
        if (reservation.getStatus() == ReservationStatusEnum.FINISHED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前预约已完成，不能重复签到。");
        }
        if (reservation.getStatus() != ReservationStatusEnum.RESERVED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅待使用预约可签到。");
        }
        requirePaidReservationWithoutRefundProcessing(reservation);
        reservation.setStatus(ReservationStatusEnum.CHECKED_IN);
        reservation.setUpdatedAt(new Date());
        reservationMapper.updateById(reservation);
        return ApiResponse.success(true);
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users() {
        assertAdmin();

        Map<Long, Long> reservationCountMap = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()).stream()
                .collect(Collectors.groupingBy(Reservation::getUserId, Collectors.counting()));
        Map<Long, Long> orderCountMap = orderMapper.selectList(Wrappers.<Order>lambdaQuery()).stream()
                .collect(Collectors.groupingBy(Order::getUserId, Collectors.counting()));

        List<Map<String, Object>> data = userMapper.selectList(Wrappers.<User>lambdaQuery()).stream()
                .sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Long::compareTo)))
                .map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("username", user.getUsername());
                    row.put("role", user.getRole());
                    row.put("status", user.getStatus());
                    row.put("statusLabel", user.getStatus() == UserStatusEnum.ENABLED ? "启用" : "禁用");
                    row.put("balance", user.getBalance());
                    row.put("createdAt", user.getCreatedAt());
                    row.put("reservationCount", reservationCountMap.getOrDefault(user.getId(), 0L));
                    row.put("orderCount", orderCountMap.getOrDefault(user.getId(), 0L));
                    return row;
                })
                .toList();
        return ApiResponse.success(data);
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        assertAdmin();

        User user = requireUser(id);
        String role = Optional.ofNullable(payload.get("role")).map(Object::toString).orElse(user.getRole());
        Integer status = readInteger(payload, "status", user.getStatus().getValue());
        Long balance = readLong(payload, "balance", user.getBalance());

        if (!List.of("USER", "ADMIN").contains(role.toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户角色不合法。");
        }

        user.setRole(role.toUpperCase(Locale.ROOT));
        user.setStatus(requireUserStatus(status));
        user.setBalance(balance);
        userMapper.updateById(user);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("role", user.getRole());
        row.put("status", user.getStatus());
        row.put("statusLabel", user.getStatus() == UserStatusEnum.ENABLED ? "启用" : "禁用");
        row.put("balance", user.getBalance());
        row.put("createdAt", user.getCreatedAt());
        return ApiResponse.success(row);
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders() {
        assertAdmin();
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .toList();
        orderWorkflowService.refreshExpiredOrders(orders);
        return ApiResponse.success(buildOrderRows(orders));
    }

    @GetMapping("/payments")
    public ApiResponse<List<Map<String, Object>>> payments() {
        assertAdmin();
        Map<Long, Order> orderMap = orderMapper.selectList(Wrappers.<Order>lambdaQuery()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Map<String, Object>> data = paymentMapper.selectList(Wrappers.<Payment>lambdaQuery()).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .map(payment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", payment.getId());
                    row.put("orderId", payment.getOrderId());
                    row.put("orderNo", Optional.ofNullable(orderMap.get(payment.getOrderId())).map(Order::getOrderNo).orElse("未知订单"));
                    row.put("paymentNo", payment.getPaymentNo());
                    row.put("bizType", payment.getBizType() == null ? null : payment.getBizType().getValue());
                    row.put("bizTypeLabel", paymentBizTypeLabel(payment.getBizType()));
                    row.put("payChannel", payment.getPayChannel());
                    row.put("payChannelLabel", payChannelLabel(payment.getPayChannel()));
                    row.put("channelTradeNo", payment.getChannelTradeNo());
                    row.put("payAmount", payment.getPayAmount());
                    row.put("payStatus", payment.getPayStatus() == null ? null : payment.getPayStatus().getValue());
                    row.put("payStatusLabel", paymentStatusLabel(payment.getPayStatus()));
                    row.put("statusNote", payment.getStatusNote());
                    row.put("paidAt", payment.getPaidAt());
                    row.put("processedAt", payment.getProcessedAt());
                    row.put("createdAt", payment.getCreatedAt());
                    row.put("reviewable", payment.getPayStatus() == PaymentStatusEnum.PROCESSING);
                    return row;
                })
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/payments/{id}/approve")
    public ApiResponse<Map<String, Object>> approvePayment(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        assertAdmin();
        String note = payload == null ? null : Optional.ofNullable(payload.get("note")).map(Object::toString).orElse(null);
        Payment payment = orderWorkflowService.reviewPayment(id, true, UserContext.getRole(), note);
        return ApiResponse.success(buildPaymentRow(payment));
    }

    @PostMapping("/payments/{id}/reject")
    public ApiResponse<Map<String, Object>> rejectPayment(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        assertAdmin();
        String note = payload == null ? null : Optional.ofNullable(payload.get("note")).map(Object::toString).orElse(null);
        Payment payment = orderWorkflowService.reviewPayment(id, false, UserContext.getRole(), note);
        return ApiResponse.success(buildPaymentRow(payment));
    }

    @PostMapping("/orders/{id}/close")
    public ApiResponse<Boolean> closeOrder(@PathVariable Long id) {
        assertAdmin();
        orderWorkflowService.closeOrder(id, UserContext.getRole(), "管理员手动关闭订单。");
        return ApiResponse.success(true);
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<Boolean> refundOrder(@PathVariable Long id) {
        assertAdmin();
        orderWorkflowService.refundOrder(id, UserContext.getRole());
        return ApiResponse.success(true);
    }

    private void assertAdmin() {
        if (!"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员权限不足。");
        }
    }

    private Venue requireVenue(Long id) {
        Venue venue = venueMapper.selectById(id);
        if (venue == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场馆不存在。");
        }
        return venue;
    }

    private VenueResource requireResource(Long id) {
        VenueResource resource = venueResourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在。");
        }
        return resource;
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在。");
        }
        return user;
    }

    private Order requireOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在。");
        }
        return order;
    }

    private String requireText(Map<String, Object> payload, String key, String message) {
        Object value = payload.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        return value.toString().trim();
    }

    private Integer readInteger(Map<String, Object> payload, String key, Integer defaultValue) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Long requireLong(Map<String, Object> payload, String key, String message) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Long readLong(Map<String, Object> payload, String key, Long defaultValue) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private ResourceTypeEnum requireResourceType(Integer code) {
        if (code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "资源类型不能为空。");
        }
        return Arrays.stream(ResourceTypeEnum.values())
                .filter(item -> Objects.equals(item.getValue(), code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "资源类型不合法。"));
    }

    private ResourceStatusEnum requireResourceStatus(Integer code) {
        if (code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "资源状态不能为空。");
        }
        return Arrays.stream(ResourceStatusEnum.values())
                .filter(item -> Objects.equals(item.getValue(), code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "资源状态不合法。"));
    }

    private ReservationStatusEnum requireReservationStatus(Integer code) {
        return Arrays.stream(ReservationStatusEnum.values())
                .filter(item -> Objects.equals(item.getValue(), code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "预约状态不合法。"));
    }

    private UserStatusEnum requireUserStatus(Integer code) {
        return Arrays.stream(UserStatusEnum.values())
                .filter(item -> Objects.equals(item.getValue(), code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "用户状态不合法。"));
    }

    private List<Map<String, Object>> buildReservationRows(List<Reservation> reservations) {
        Set<Long> userIds = reservations.stream().map(Reservation::getUserId).collect(Collectors.toSet());
        Set<Long> venueIds = reservations.stream().map(Reservation::getVenueId).collect(Collectors.toSet());
        Set<Long> resourceIds = reservations.stream().map(Reservation::getResourceId).collect(Collectors.toSet());

        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Venue> venueMap = venueIds.isEmpty() ? Map.of() : venueMapper.selectBatchIds(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        Map<Long, VenueResource> resourceMap = resourceIds.isEmpty() ? Map.of() : venueResourceMapper.selectBatchIds(resourceIds).stream()
                .collect(Collectors.toMap(VenueResource::getId, Function.identity()));

        return reservations.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("userId", item.getUserId());
            row.put("username", Optional.ofNullable(userMap.get(item.getUserId())).map(User::getUsername).orElse("未知用户"));
            row.put("venueId", item.getVenueId());
            row.put("venueName", Optional.ofNullable(venueMap.get(item.getVenueId())).map(Venue::getName).orElse("未知场馆"));
            row.put("resourceId", item.getResourceId());
            row.put("resourceName", Optional.ofNullable(resourceMap.get(item.getResourceId())).map(VenueResource::getName).orElse("未知资源"));
            row.put("slotDate", item.getSlotDate());
            row.put("size", item.getSize());
            row.put("startUnit", item.getStartUnit());
            row.put("endUnit", item.getEndUnit());
            row.put("status", item.getStatus().getValue());
            row.put("statusLabel", reservationStatusLabel(item.getStatus()));
            row.put("createdAt", item.getCreatedAt());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> buildOrderRows(List<Order> orders) {
        Set<Long> userIds = orders.stream().map(Order::getUserId).collect(Collectors.toSet());
        Set<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());

        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Reservation> reservationMap = orderIds.isEmpty() ? Map.of() : reservationMapper.selectList(
                        Wrappers.<Reservation>lambdaQuery().in(Reservation::getOrderId, orderIds)
                ).stream()
                .collect(Collectors.toMap(Reservation::getOrderId, Function.identity(), (a, b) -> a));
        Map<Long, Payment> paymentMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            paymentMapper.selectList(
                            Wrappers.<Payment>lambdaQuery()
                                    .in(Payment::getOrderId, orderIds)
                                    .orderByDesc(Payment::getCreatedAt)
                    )
                    .forEach(item -> paymentMap.putIfAbsent(item.getOrderId(), item));
        }

        return orders.stream().map(order -> {
            Reservation reservation = reservationMap.get(order.getId());
            Payment payment = paymentMap.get(order.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            row.put("orderNo", order.getOrderNo());
            row.put("userId", order.getUserId());
            row.put("username", Optional.ofNullable(userMap.get(order.getUserId())).map(User::getUsername).orElse("未知用户"));
            row.put("totalAmount", order.getTotalAmount());
            row.put("status", order.getStatus().getValue());
            row.put("statusLabel", orderStatusLabel(order.getStatus()));
            row.put("expiredAt", order.getExpiredAt());
            row.put("createdAt", order.getCreatedAt());
            row.put("updatedAt", order.getUpdatedAt());
            if (reservation != null) {
                row.put("reservationId", reservation.getId());
                row.put("venueId", reservation.getVenueId());
                row.put("resourceId", reservation.getResourceId());
                row.put("slotDate", reservation.getSlotDate());
            }
            if (payment != null) {
                row.put("paymentId", payment.getId());
                row.put("paymentNo", payment.getPaymentNo());
                row.put("paymentBizType", payment.getBizType() == null ? null : payment.getBizType().getValue());
                row.put("paymentBizTypeLabel", paymentBizTypeLabel(payment.getBizType()));
                row.put("payChannel", payment.getPayChannel());
                row.put("payChannelLabel", payChannelLabel(payment.getPayChannel()));
                row.put("channelTradeNo", payment.getChannelTradeNo());
                row.put("payStatus", payment.getPayStatus() == null ? null : payment.getPayStatus().getValue());
                row.put("payStatusLabel", paymentStatusLabel(payment.getPayStatus()));
                row.put("statusNote", payment.getStatusNote());
                row.put("paidAt", payment.getPaidAt());
                row.put("processedAt", payment.getProcessedAt());
            }
            return row;
        }).toList();
    }

    private Map<String, Object> toVenueRow(Venue venue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", venue.getId());
        row.put("name", venue.getName());
        row.put("type", venue.getType());
        row.put("status", venue.getStatus());
        row.put("createdAt", venue.getCreatedAt());
        return row;
    }

    private Map<String, Object> toResourceRow(VenueResource resource) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", resource.getId());
        row.put("venueId", resource.getVenueId());
        row.put("name", resource.getName());
        row.put("resourceType", resource.getResourceType().getValue());
        row.put("resourceTypeLabel", resource.getResourceType().getDesc());
        row.put("capacity", resource.getCapacity());
        row.put("price", resource.getPrice());
        row.put("unitMinutes", resource.getUnitMinutes());
        row.put("status", resource.getStatus().getValue());
        row.put("statusLabel", resource.getStatus() == ResourceStatusEnum.ENABLED ? "启用" : "禁用");
        return row;
    }

    private Map<String, Object> toAdminResourceRow(Map<String, Object> resource) {
        Integer resourceTypeCode = readRowInteger(resource, "resourceTypeCode", null);
        Integer statusCode = readRowInteger(resource, "resourceStatus", null);
        ResourceTypeEnum resourceType = resourceTypeCode == null ? null : requireResourceType(resourceTypeCode);
        ResourceStatusEnum status = statusCode == null ? null : requireResourceStatus(statusCode);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", readRowLong(resource, "resourceId", null));
        row.put("venueId", readRowLong(resource, "venueId", null));
        row.put("name", readRowText(resource, "resourceName"));
        row.put("resourceType", resourceTypeCode);
        row.put("resourceTypeLabel", resourceType == null ? "未知" : resourceType.getDesc());
        row.put("capacity", readRowInteger(resource, "capacity", 0));
        row.put("price", readRowInteger(resource, "price", 0));
        row.put("unitMinutes", readRowInteger(resource, "unitMinutes", 10));
        row.put("status", statusCode);
        row.put("statusLabel", status == ResourceStatusEnum.ENABLED ? "启用" : "禁用");
        return row;
    }

    private String reservationStatusLabel(ReservationStatusEnum status) {
        return switch (status) {
            case QUEUING -> "排队中";
            case RESERVED -> "待使用";
            case CHECKED_IN -> "使用中";
            case CANCELLED -> "已取消";
            case EXPIRED -> "已释放";
            case FINISHED -> "已完成";
        };
    }

    private Order requirePaidReservationWithoutRefundProcessing(Reservation reservation) {
        if (reservation.getOrderId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前预约未关联支付订单，不能执行履约操作。");
        }
        Order order = orderMapper.selectById(reservation.getOrderId());
        if (order == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约关联订单不存在。");
        }
        if (order.getStatus() != OrderStatusEnum.PAID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已支付订单对应的预约可执行履约操作。");
        }
        long processingRefundCount = paymentMapper.selectCount(
                Wrappers.<Payment>lambdaQuery()
                        .eq(Payment::getOrderId, order.getId())
                        .eq(Payment::getBizType, PaymentBizTypeEnum.REFUND)
                        .eq(Payment::getPayStatus, PaymentStatusEnum.PROCESSING)
        );
        if (processingRefundCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单退款处理中，不能执行履约操作。");
        }
        return order;
    }

    private String orderStatusLabel(OrderStatusEnum status) {
        return switch (status) {
            case UNPAID -> "未支付";
            case PAID -> "已支付";
            case CLOSED -> "已取消";
            case REFUNDED -> "已退款";
        };
    }

    private Map<String, Object> buildPaymentRow(Payment payment) {
        Order order = orderMapper.selectById(payment.getOrderId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("orderId", payment.getOrderId());
        row.put("orderNo", order == null ? "未知订单" : order.getOrderNo());
        row.put("paymentNo", payment.getPaymentNo());
        row.put("bizType", payment.getBizType() == null ? null : payment.getBizType().getValue());
        row.put("bizTypeLabel", paymentBizTypeLabel(payment.getBizType()));
        row.put("payChannel", payment.getPayChannel());
        row.put("payChannelLabel", payChannelLabel(payment.getPayChannel()));
        row.put("channelTradeNo", payment.getChannelTradeNo());
        row.put("payAmount", payment.getPayAmount());
        row.put("payStatus", payment.getPayStatus() == null ? null : payment.getPayStatus().getValue());
        row.put("payStatusLabel", paymentStatusLabel(payment.getPayStatus()));
        row.put("statusNote", payment.getStatusNote());
        row.put("paidAt", payment.getPaidAt());
        row.put("processedAt", payment.getProcessedAt());
        row.put("createdAt", payment.getCreatedAt());
        row.put("reviewable", payment.getPayStatus() == PaymentStatusEnum.PROCESSING);
        return row;
    }

    private boolean reservationMatchesKeyword(
            Reservation item,
            String filterKeyword,
            Map<Long, String> userNameMap,
            Map<Long, String> venueNameMap,
            Map<Long, VenueResource> resourceMap
    ) {
        if (filterKeyword == null || filterKeyword.isBlank()) {
            return true;
        }
        String username = Optional.ofNullable(userNameMap.get(item.getUserId())).orElse("").toLowerCase(Locale.ROOT);
        String venueName = Optional.ofNullable(venueNameMap.get(item.getVenueId())).orElse("").toLowerCase(Locale.ROOT);
        String resourceName = Optional.ofNullable(resourceMap.get(item.getResourceId())).map(VenueResource::getName).orElse("").toLowerCase(Locale.ROOT);
        return username.contains(filterKeyword)
                || venueName.contains(filterKeyword)
                || resourceName.contains(filterKeyword)
                || String.valueOf(item.getId()).contains(filterKeyword);
    }

    private Date parseDateOnly(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(text.trim());
        } catch (java.text.ParseException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预约日期格式不正确。");
        }
    }

    private boolean sameDay(Date left, Date right) {
        if (left == null || right == null) {
            return false;
        }
        Calendar c1 = Calendar.getInstance();
        c1.setTime(left);
        Calendar c2 = Calendar.getInstance();
        c2.setTime(right);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private String paymentBizTypeLabel(PaymentBizTypeEnum bizType) {
        return bizType == null ? "未知" : bizType.getDesc();
    }

    private String paymentStatusLabel(PaymentStatusEnum status) {
        return status == null ? "未知" : status.getDesc();
    }

    private String payChannelLabel(Integer payChannel) {
        return switch (payChannel == null ? -1 : payChannel) {
            case 1 -> "钱包余额";
            case 2 -> "模拟网关";
            default -> "未知渠道";
        };
    }

    private Long readRowLong(Map<String, Object> row, String key, Long defaultValue) {
        Object value = readRowValue(row, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer readRowInteger(Map<String, Object> row, String key, Integer defaultValue) {
        Object value = readRowValue(row, key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private String readRowText(Map<String, Object> row, String key) {
        Object value = readRowValue(row, key);
        return value == null ? "" : value.toString();
    }

    private Object readRowValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String normalizedKey = normalizeRowKey(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (normalizeRowKey(entry.getKey()).equals(normalizedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeRowKey(String key) {
        return key == null ? "" : key.replace("_", "").toLowerCase();
    }
}
