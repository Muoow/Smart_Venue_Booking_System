package com.courtflow.homework.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.OrderStatusEnum;
import com.courtflow.homework.common.enums.PaymentBizTypeEnum;
import com.courtflow.homework.common.enums.PaymentStatusEnum;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.enums.UserStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.utils.BusinessIdGenerator;
import com.courtflow.homework.entity.Order;
import com.courtflow.homework.entity.Payment;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.User;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.PaymentMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.UserMapper;
import com.courtflow.homework.service.OrderWorkflowService;
import com.courtflow.homework.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class OrderWorkflowServiceImpl implements OrderWorkflowService {

    private static final int CHANNEL_WALLET = 1;
    private static final int CHANNEL_SIMULATED_GATEWAY = 2;

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationService reservationService;
    private final BusinessIdGenerator businessIdGenerator;

    public OrderWorkflowServiceImpl(
            OrderMapper orderMapper,
            PaymentMapper paymentMapper,
            UserMapper userMapper,
            ReservationMapper reservationMapper,
            ReservationService reservationService,
            BusinessIdGenerator businessIdGenerator
    ) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.userMapper = userMapper;
        this.reservationMapper = reservationMapper;
        this.reservationService = reservationService;
        this.businessIdGenerator = businessIdGenerator;
    }

    @Override
    @Transactional
    public Order refreshOrderState(Order order) {
        if (order == null) {
            return null;
        }
        if (order.getStatus() == OrderStatusEnum.UNPAID && isExpired(order)) {
            return closeOrderInternal(order, "SYSTEM", "订单超时自动关闭。", true);
        }
        return order;
    }

    @Override
    @Transactional
    public void refreshExpiredOrders(Collection<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (Order order : orders) {
            refreshOrderState(order);
        }
    }

    @Override
    @Transactional
    public boolean closeExpiredOrder(Long orderId) {
        Order order = requireOrder(orderId);
        return closeOrderInternal(order, "SYSTEM", "订单超时自动关闭。", true).getStatus() == OrderStatusEnum.CLOSED;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public Payment payOrder(Long orderId, Long userId, Integer payChannel) {
        Order order = requireOrder(orderId);
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权支付该订单。");
        }

        order = refreshOrderState(order);
        if (order.getStatus() != OrderStatusEnum.UNPAID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单状态不能支付。");
        }

        User user = requireEnabledUser(userId);
        int channel = normalizeChannel(payChannel);
        Date now = new Date();
        Payment existingProcessingPayment = latestProcessingPayment(order.getId(), PaymentBizTypeEnum.PAY);
        if (existingProcessingPayment != null) {
            return existingProcessingPayment;
        }

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .paymentNo(generatePaymentNo("PAY"))
                .bizType(PaymentBizTypeEnum.PAY)
                .payChannel(channel)
                .payAmount(order.getTotalAmount())
                .payStatus(PaymentStatusEnum.PROCESSING)
                .statusNote("支付申请已受理，进入内部清算流程。")
                .createdAt(now)
                .build();
        paymentMapper.insert(payment);

        if (channel == CHANNEL_WALLET) {
            processWalletPayment(order, user, payment, now);
        } else {
            processGatewayPayment(order, user, payment, now);
        }
        return paymentMapper.selectById(payment.getId());
    }

    @Override
    @Transactional
    public Payment refundOrder(Long orderId, String operator) {
        Order order = requireOrder(orderId);
        if (order.getStatus() != OrderStatusEnum.PAID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已支付订单支持退款。");
        }
        Reservation reservation = findReservationByOrder(orderId);
        if (reservation != null && (reservation.getStatus() == ReservationStatusEnum.CHECKED_IN
                || reservation.getStatus() == ReservationStatusEnum.FINISHED)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已签到或已履约预约不支持退款。");
        }

        Payment sourcePayment = latestSuccessfulPayment(orderId);
        if (sourcePayment == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未找到可退款的成功支付流水。");
        }
        if (hasSuccessfulRefund(orderId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该订单已完成退款。");
        }
        Payment existingProcessingRefund = latestProcessingPayment(orderId, PaymentBizTypeEnum.REFUND);
        if (existingProcessingRefund != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该订单已有退款审核处理中。");
        }

        Date now = new Date();
        Payment refundPayment = Payment.builder()
                .orderId(order.getId())
                .paymentNo(generatePaymentNo("REF"))
                .bizType(PaymentBizTypeEnum.REFUND)
                .payChannel(sourcePayment.getPayChannel())
                .channelTradeNo(generateChannelTradeNo("RF"))
                .payAmount(order.getTotalAmount())
                .payStatus(PaymentStatusEnum.PROCESSING)
                .statusNote("退款申请已受理，进入内部退款流程。")
                .createdAt(now)
                .build();
        paymentMapper.insert(refundPayment);

        String operatorName = operator == null || operator.isBlank() ? "SYSTEM" : operator.trim();
        if (Objects.equals(sourcePayment.getPayChannel(), CHANNEL_WALLET)) {
            User user = requireUser(order.getUserId());
            user.setBalance(safeBalance(user.getBalance()) + safeAmount(order.getTotalAmount()));
            userMapper.updateById(user);
            refundPayment.setPayStatus(PaymentStatusEnum.SUCCESS);
            refundPayment.setStatusNote("已退回用户钱包余额，操作人：" + operatorName);
            refundPayment.setPaidAt(now);
            refundPayment.setProcessedAt(now);
            paymentMapper.updateById(refundPayment);

            order.setStatus(OrderStatusEnum.REFUNDED);
            order.setUpdatedAt(now);
            orderMapper.updateById(order);

            cancelReservationAfterRefund(order.getId());
        } else {
            refundPayment.setStatusNote("模拟网关退款申请已提交，待管理员审核。操作人：" + operatorName);
            paymentMapper.updateById(refundPayment);
        }

        return paymentMapper.selectById(refundPayment.getId());
    }

    @Override
    @Transactional
    public Payment reviewPayment(Long paymentId, boolean approve, String operator, String note) {
        Payment payment = requirePayment(paymentId);
        if (payment.getPayStatus() != PaymentStatusEnum.PROCESSING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前支付记录不是待审核状态。");
        }

        return payment.getBizType() == PaymentBizTypeEnum.REFUND
                ? reviewRefundPayment(payment, approve, operator, note)
                : reviewPayPayment(payment, approve, operator, note);
    }

    @Override
    @Transactional
    public Order closeOrder(Long orderId, String operator, String reason) {
        Order order = requireOrder(orderId);
        return closeOrderInternal(order, operator, reason, false);
    }

    private void processWalletPayment(Order order, User user, Payment payment, Date now) {
        long balance = safeBalance(user.getBalance());
        long totalAmount = safeAmount(order.getTotalAmount());
        if (balance < totalAmount) {
            payment.setPayStatus(PaymentStatusEnum.FAILED);
            payment.setStatusNote("钱包余额不足，支付失败。");
            payment.setProcessedAt(now);
            paymentMapper.updateById(payment);
            throw new BusinessException(ResultCode.BAD_REQUEST, "钱包余额不足，无法完成支付。");
        }

        user.setBalance(balance - totalAmount);
        userMapper.updateById(user);

        payment.setPayStatus(PaymentStatusEnum.SUCCESS);
        payment.setStatusNote("钱包扣款成功，已完成内部记账。");
        payment.setPaidAt(now);
        payment.setProcessedAt(now);
        paymentMapper.updateById(payment);

        order.setStatus(OrderStatusEnum.PAID);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
    }

    private void processGatewayPayment(Order order, User user, Payment payment, Date now) {
        Reservation reservation = findReservationByOrder(order.getId());
        int historicalRefundCount = Math.toIntExact(orderMapper.selectCount(
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, user.getId())
                        .eq(Order::getStatus, OrderStatusEnum.REFUNDED)
        ));
        int riskScore = calculateRiskScore(order, reservation, historicalRefundCount);
        String channelTradeNo = generateChannelTradeNo("SIM");
        payment.setChannelTradeNo(channelTradeNo);

        if (riskScore >= 80) {
            payment.setPayStatus(PaymentStatusEnum.FAILED);
            payment.setStatusNote("模拟网关风控拦截，风险分=" + riskScore + "。");
            payment.setProcessedAt(now);
            paymentMapper.updateById(payment);
            throw new BusinessException(ResultCode.BAD_REQUEST, "支付未通过内部风控校验，请更换支付方式或联系管理员。");
        }

        payment.setStatusNote("模拟网关风控通过，风险分=" + riskScore + "，待管理员审核。");
        paymentMapper.updateById(payment);
    }

    private int calculateRiskScore(Order order, Reservation reservation, int historicalRefundCount) {
        int score = 5;
        long amount = safeAmount(order.getTotalAmount());
        if (amount >= 100_000) {
            score += 55;
        } else if (amount >= 30_000) {
            score += 25;
        } else if (amount >= 10_000) {
            score += 10;
        }

        if (historicalRefundCount >= 3) {
            score += 35;
        } else if (historicalRefundCount >= 1) {
            score += 10;
        }

        if (reservation != null && reservation.getSlotDate() != null) {
            long gapMs = reservation.getSlotDate().getTime() - System.currentTimeMillis();
            if (gapMs < 0) {
                score += 20;
            } else if (gapMs <= 6L * 60 * 60 * 1000) {
                score += 15;
            }
        }
        return score;
    }

    private Order closeOrderInternal(Order order, String operator, String reason, boolean expiredOnly) {
        if (order.getStatus() == OrderStatusEnum.CLOSED) {
            return order;
        }
        if (order.getStatus() != OrderStatusEnum.UNPAID) {
            if (expiredOnly) {
                return order;
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅未支付订单可关闭。");
        }
        if (expiredOnly && !isExpired(order)) {
            return order;
        }

        Date now = new Date();
        order.setStatus(OrderStatusEnum.CLOSED);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);

        String note = (reason == null || reason.isBlank() ? "订单已关闭。" : reason.trim()) +
                " 操作人：" + (operator == null || operator.isBlank() ? "SYSTEM" : operator.trim());
        List<Payment> processingPayments = paymentMapper.selectList(
                Wrappers.<Payment>lambdaQuery()
                        .eq(Payment::getOrderId, order.getId())
                        .eq(Payment::getPayStatus, PaymentStatusEnum.PROCESSING)
        );
        for (Payment item : processingPayments) {
            item.setPayStatus(PaymentStatusEnum.CLOSED);
            item.setStatusNote(note);
            item.setProcessedAt(now);
            paymentMapper.updateById(item);
        }

        Reservation reservation = findReservationByOrder(order.getId());
        if (reservation != null && (reservation.getStatus() == ReservationStatusEnum.QUEUING || reservation.getStatus() == ReservationStatusEnum.RESERVED)) {
            reservationService.close(reservation.getId(),
                    expiredOnly ? ReservationStatusEnum.EXPIRED : ReservationStatusEnum.CANCELLED);
        }
        return order;
    }

    private Payment reviewPayPayment(Payment payment, boolean approve, String operator, String note) {
        Order order = requireOrder(payment.getOrderId());
        if (order.getStatus() != OrderStatusEnum.UNPAID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单状态不允许审核支付。");
        }

        Date now = new Date();
        String decisionNote = buildReviewNote(approve, operator, note);
        if (approve) {
            payment.setPayStatus(PaymentStatusEnum.SUCCESS);
            payment.setStatusNote(decisionNote);
            payment.setPaidAt(now);
            payment.setProcessedAt(now);
            paymentMapper.updateById(payment);

            order.setStatus(OrderStatusEnum.PAID);
            order.setUpdatedAt(now);
            orderMapper.updateById(order);
        } else {
            payment.setPayStatus(PaymentStatusEnum.FAILED);
            payment.setStatusNote(decisionNote);
            payment.setProcessedAt(now);
            paymentMapper.updateById(payment);
        }
        return paymentMapper.selectById(payment.getId());
    }

    private Payment reviewRefundPayment(Payment payment, boolean approve, String operator, String note) {
        Order order = requireOrder(payment.getOrderId());
        if (order.getStatus() != OrderStatusEnum.PAID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单状态不允许审核退款。");
        }

        Date now = new Date();
        String decisionNote = buildReviewNote(approve, operator, note);
        if (approve) {
            payment.setPayStatus(PaymentStatusEnum.SUCCESS);
            payment.setStatusNote(decisionNote);
            payment.setPaidAt(now);
            payment.setProcessedAt(now);
            paymentMapper.updateById(payment);

            order.setStatus(OrderStatusEnum.REFUNDED);
            order.setUpdatedAt(now);
            orderMapper.updateById(order);

            cancelReservationAfterRefund(order.getId());
        } else {
            payment.setPayStatus(PaymentStatusEnum.FAILED);
            payment.setStatusNote(decisionNote);
            payment.setProcessedAt(now);
            paymentMapper.updateById(payment);
        }
        return paymentMapper.selectById(payment.getId());
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在。");
        }
        return order;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在。");
        }
        return user;
    }

    private Payment requirePayment(Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "支付记录不存在。");
        }
        return payment;
    }

    private User requireEnabledUser(Long userId) {
        User user = requireUser(userId);
        if (user.getStatus() != UserStatusEnum.ENABLED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前用户状态不可支付。");
        }
        return user;
    }

    private Reservation findReservationByOrder(Long orderId) {
        List<Reservation> reservations = reservationMapper.selectList(
                Wrappers.<Reservation>lambdaQuery().eq(Reservation::getOrderId, orderId)
        );
        return reservations.stream()
                .sorted(Comparator.comparing(Reservation::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .findFirst()
                .orElse(null);
    }

    private Payment latestSuccessfulPayment(Long orderId) {
        return paymentMapper.selectList(
                Wrappers.<Payment>lambdaQuery()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getBizType, PaymentBizTypeEnum.PAY)
                        .eq(Payment::getPayStatus, PaymentStatusEnum.SUCCESS)
        ).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .findFirst()
                .orElse(null);
    }

    private Payment latestProcessingPayment(Long orderId, PaymentBizTypeEnum bizType) {
        return paymentMapper.selectList(
                Wrappers.<Payment>lambdaQuery()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getBizType, bizType)
                        .eq(Payment::getPayStatus, PaymentStatusEnum.PROCESSING)
        ).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .findFirst()
                .orElse(null);
    }

    private boolean hasSuccessfulRefund(Long orderId) {
        return paymentMapper.selectCount(
                Wrappers.<Payment>lambdaQuery()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getBizType, PaymentBizTypeEnum.REFUND)
                        .eq(Payment::getPayStatus, PaymentStatusEnum.SUCCESS)
        ) > 0;
    }

    private boolean isExpired(Order order) {
        return order.getExpiredAt() != null && order.getExpiredAt().before(new Date());
    }

    private int normalizeChannel(Integer payChannel) {
        int channel = payChannel == null ? CHANNEL_SIMULATED_GATEWAY : payChannel;
        if (channel != CHANNEL_WALLET && channel != CHANNEL_SIMULATED_GATEWAY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "暂不支持该支付渠道。");
        }
        return channel;
    }

    private long safeBalance(Long balance) {
        return balance == null ? 0L : balance;
    }

    private long safeAmount(Long amount) {
        return amount == null ? 0L : Math.max(amount, 0L);
    }

    private String generatePaymentNo(String prefix) {
        return businessIdGenerator.nextId(prefix);
    }

    private String generateChannelTradeNo(String prefix) {
        return businessIdGenerator.nextId(prefix);
    }

    private String buildReviewNote(boolean approve, String operator, String note) {
        String operatorName = operator == null || operator.isBlank() ? "ADMIN" : operator.trim();
        String suffix = note == null || note.isBlank() ? "" : " 说明：" + note.trim();
        return (approve ? "管理员审核通过。" : "管理员审核驳回。") + " 审核人：" + operatorName + suffix;
    }

    private void cancelReservationAfterRefund(Long orderId) {
        Reservation reservation = findReservationByOrder(orderId);
        if (reservation != null && (reservation.getStatus() == ReservationStatusEnum.QUEUING || reservation.getStatus() == ReservationStatusEnum.RESERVED)) {
            reservationService.close(reservation.getId(), ReservationStatusEnum.CANCELLED);
        }
    }
}
