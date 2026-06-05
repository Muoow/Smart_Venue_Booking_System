package com.courtflow.homework.common.utils;

import com.courtflow.homework.common.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ReservationMessagePublisher {

    private static final long PUBLISH_CONFIRM_TIMEOUT_SECONDS = 5L;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    public boolean publish(Long reservationId, Long resourceId) {
        if (rabbitTemplate == null || reservationId == null || resourceId == null) {
            return false;
        }

        int partition = Math.floorMod(resourceId.intValue(), MqConstant.QUEUE_PARTITIONS);
        CorrelationData correlationData = new CorrelationData("reservation-" + reservationId + "-" + System.nanoTime());

        try {
            rabbitTemplate.convertAndSend(
                    MqConstant.RESERVATION_EXCHANGE,
                    MqConstant.RESERVATION_ROUTING_KEY + partition,
                    String.valueOf(reservationId),
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setContentType("text/plain");
                        return message;
                    },
                    correlationData
            );

            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(PUBLISH_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                log.error("Reservation message publish not acknowledged. reservationId={}, cause={}",
                        reservationId, confirm.getReason());
                return false;
            }

            if (correlationData.getReturned() != null) {
                log.error("Reservation message returned by broker. reservationId={}, replyText={}",
                        reservationId, correlationData.getReturned().getReplyText());
                return false;
            }

            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting reservation publish confirm. reservationId={}", reservationId, ex);
            return false;
        } catch (Exception ex) {
            log.error("Failed to publish reservation message. reservationId={}", reservationId, ex);
            return false;
        }
    }
}
