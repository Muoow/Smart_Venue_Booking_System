package com.courtflow.homework.service;

import com.courtflow.homework.entity.Order;
import com.courtflow.homework.entity.Payment;

import java.util.Collection;

public interface OrderWorkflowService {

    Order refreshOrderState(Order order);

    void refreshExpiredOrders(Collection<Order> orders);

    boolean closeExpiredOrder(Long orderId);

    Payment payOrder(Long orderId, Long userId, Integer payChannel);

    Payment refundOrder(Long orderId, String operator);

    Payment reviewPayment(Long paymentId, boolean approve, String operator, String note);

    Order closeOrder(Long orderId, String operator, String reason);
}
