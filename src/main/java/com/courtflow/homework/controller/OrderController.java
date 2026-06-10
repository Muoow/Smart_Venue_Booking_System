package com.courtflow.homework.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.OrderStatusEnum;
import com.courtflow.homework.common.enums.PaymentBizTypeEnum;
import com.courtflow.homework.common.enums.PaymentStatusEnum;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.utils.BusinessIdGenerator;
import com.courtflow.homework.entity.Order;
import com.courtflow.homework.entity.Payment;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.PaymentMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.OrderWorkflowService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderMapper orderMapper;

    private final PaymentMapper paymentMapper;

    private final ReservationMapper reservationMapper;

    private final VenueResourceMapper venueResourceMapper;

    private final OrderWorkflowService orderWorkflowService;

    private final BusinessIdGenerator businessIdGenerator;

    public OrderController(
            OrderMapper orderMapper,
            PaymentMapper paymentMapper,
            ReservationMapper reservationMapper,
            VenueResourceMapper venueResourceMapper,
            OrderWorkflowService orderWorkflowService,
            BusinessIdGenerator businessIdGenerator
    ) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.reservationMapper = reservationMapper;
        this.venueResourceMapper = venueResourceMapper;
        this.orderWorkflowService = orderWorkflowService;
        this.businessIdGenerator = businessIdGenerator;
    }

    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> myOrders() {
        Long userId = requireUserId();

        List<Order> orders = orderMapper.selectList(
                Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId)
        ).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .toList();
        orderWorkflowService.refreshExpiredOrders(orders);
        return ApiResponse.success(buildOrderRows(orders));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Long userId = requireUserId();
        Order order = requireOrder(id);
        order = orderWorkflowService.refreshOrderState(order);
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单。");
        }
        return ApiResponse.success(toOrderRow(order, findReservationByOrder(order.getId()), latestPayment(order.getId())));
    }

    @PostMapping("/create")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        Long userId = requireUserId();
        Long reservationId = requireLong(payload, "reservationId", "预约编号不能为空。");

        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约记录不存在。");
        }
        if (!Objects.equals(reservation.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权为该预约创建订单。");
        }
        if (reservation.getOrderId() != null) {
            Order existing = orderMapper.selectById(reservation.getOrderId());
            if (existing != null) {
                existing = orderWorkflowService.refreshOrderState(existing);
                if (existing.getStatus() != OrderStatusEnum.CLOSED) {
                    return ApiResponse.success(
                            toOrderRow(existing, reservation, latestPayment(existing.getId())),
                            "订单已存在，返回当前订单信息。"
                    );
                }
            }
        }
        if (reservation.getStatus() == ReservationStatusEnum.CANCELLED
                || reservation.getStatus() == ReservationStatusEnum.FINISHED
                || reservation.getStatus() == ReservationStatusEnum.EXPIRED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前预约状态不能创建订单。");
        }
        if (reservation.getStatus() == ReservationStatusEnum.QUEUING) {
            throw new BusinessException(ResultCode.CONFLICT, "预约正在处理中，请等待占位完成后再创建订单。");
        }
        if (reservation.getStatus() != ReservationStatusEnum.RESERVED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅待使用预约可创建订单。");
        }

        VenueResource resource = venueResourceMapper.selectById(reservation.getResourceId());
        if (resource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约资源不存在。");
        }

        int unitCount = reservation.getEndUnit() - reservation.getStartUnit() + 1;
        long totalAmount = (long) unitCount * Math.max(resource.getPrice(), 0);

        Order order = Order.builder()
                .orderNo(generateOrderNo())
                .userId(userId)
                .totalAmount(totalAmount)
                .status(OrderStatusEnum.UNPAID)
                .expiredAt(new Date(System.currentTimeMillis() + 30L * 60 * 1000))
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        orderMapper.insert(order);

        reservation.setOrderId(order.getId());
        reservation.setUpdatedAt(new Date());
        reservationMapper.updateById(reservation);

        return ApiResponse.success(toOrderRow(order, reservation, null));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<Map<String, Object>> pay(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        Long userId = requireUserId();
        int payChannel = payload == null ? 1 : readInteger(payload, "payChannel", 1);
        Payment payment = orderWorkflowService.payOrder(id, userId, payChannel);
        Order order = requireOrder(id);
        return ApiResponse.success(toOrderRow(order, findReservationByOrder(order.getId()), payment));
    }

    private List<Map<String, Object>> buildOrderRows(List<Order> orders) {
        Map<Long, Reservation> reservationMap = findReservationsByOrders(orders);
        Map<Long, Payment> paymentMap = findPaymentsByOrders(orders);
        return orders.stream()
                .map(order -> toOrderRow(order, reservationMap.get(order.getId()), paymentMap.get(order.getId())))
                .toList();
    }

    private Map<Long, Reservation> findReservationsByOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        Set<Long> orderIds = orders.stream().map(Order::getId).collect(HashSet::new, HashSet::add, HashSet::addAll);
        return reservationMapper.selectList(
                        Wrappers.<Reservation>lambdaQuery().in(Reservation::getOrderId, orderIds)
                ).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getOrderId(), item), HashMap::putAll);
    }

    private Map<Long, Payment> findPaymentsByOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        Set<Long> orderIds = orders.stream().map(Order::getId).collect(HashSet::new, HashSet::add, HashSet::addAll);
        Map<Long, Payment> paymentMap = new HashMap<>();
        paymentMapper.selectList(
                        Wrappers.<Payment>lambdaQuery().in(Payment::getOrderId, orderIds)
                ).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .forEach(item -> paymentMap.putIfAbsent(item.getOrderId(), item));
        return paymentMap;
    }

    private Reservation findReservationByOrder(Long orderId) {
        List<Reservation> reservations = reservationMapper.selectList(
                Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getOrderId, orderId)
                        .orderByDesc(Reservation::getCreatedAt)
        );
        return reservations.isEmpty() ? null : reservations.get(0);
    }

    private Payment latestPayment(Long orderId) {
        List<Payment> payments = paymentMapper.selectList(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, orderId)
        ).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .toList();
        return payments.isEmpty() ? null : payments.get(0);
    }

    private Map<String, Object> toOrderRow(Order order, Reservation reservation, Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("userId", order.getUserId());
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
            row.put("startUnit", reservation.getStartUnit());
            row.put("endUnit", reservation.getEndUnit());
            row.put("reservationStatus", reservation.getStatus().getValue());
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
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录。");
        }
        return userId;
    }

    private Order requireOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在。");
        }
        return order;
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

    private String generateOrderNo() {
        return businessIdGenerator.nextId("CF");
    }

    private String orderStatusLabel(OrderStatusEnum status) {
        return switch (status) {
            case UNPAID -> "未支付";
            case PAID -> "已支付";
            case CLOSED -> "已取消";
            case REFUNDED -> "已退款";
        };
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
}
