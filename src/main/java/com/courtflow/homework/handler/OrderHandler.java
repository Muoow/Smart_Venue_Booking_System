package com.courtflow.homework.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.constant.MqConstant;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
public class OrderHandler {

    private OrderMapper orderMapper;

    public OrderHandler(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = MqConstant.ORDER_QUEUE)
    public void handleStatusMessage(Map<String, Object> msg) {

    }
}
