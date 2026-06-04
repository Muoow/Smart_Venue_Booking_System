package com.courtflow.homework.handler;

import com.courtflow.homework.common.constant.MqConstant;
import com.courtflow.homework.service.OrderWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "courtflow.middleware.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OrderHandler {

    private final OrderWorkflowService orderWorkflowService;

    public OrderHandler(OrderWorkflowService orderWorkflowService) {
        this.orderWorkflowService = orderWorkflowService;
    }

    @RabbitListener(queues = MqConstant.ORDER_QUEUE)
    public void handleStatusMessage(Map<String, Object> msg) {
        if (msg == null || msg.isEmpty()) {
            log.warn("Ignore empty order message.");
            return;
        }
        String action = msg.getOrDefault("action", "").toString();
        Long orderId = parseLong(msg.get("orderId"));
        if (orderId == null) {
            log.warn("Ignore order message without orderId: {}", msg);
            return;
        }

        switch (action) {
            case "CHECK_EXPIRE", "AUTO_CLOSE_EXPIRED" -> orderWorkflowService.closeExpiredOrder(orderId);
            case "REFUND_ORDER" -> orderWorkflowService.refundOrder(orderId, "MQ");
            default -> log.warn("Unsupported order message action: {}", action);
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
