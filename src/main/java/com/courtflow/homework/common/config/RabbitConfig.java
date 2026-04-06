package com.courtflow.homework.common.config;

import com.courtflow.homework.common.constant.MqConstant;
import com.courtflow.homework.handler.ReservationHandler;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class RabbitConfig {

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private Integer port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();

        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        factory.setChannelCacheSize(50);

        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        template.setMandatory(true);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String correlationId = correlationData != null ? correlationData.getId() : null;
            if (ack) {
                log.info("Message successfully published to exchange. correlationId={}", correlationId);
            } else {
                log.error("Failed to publish message to exchange. correlationId={}, cause={}", correlationId, cause);
            }
        });
        template.setReturnsCallback(returned ->
                log.error(
                        "Message routing failed. exchange={}, routingKey={}, replyCode={}, replyText={}",
                        returned.getExchange(),
                        returned.getRoutingKey(),
                        returned.getReplyCode(),
                        returned.getReplyText()
                )
        );

        return template;
    }

    @Bean
    public DirectExchange reservationExchange() {
        return new DirectExchange(MqConstant.RESERVATION_EXCHANGE);
    }

    @Bean
    public List<Queue> reservationQueues() {
        List<Queue> queues = new ArrayList<>();
        for(int i = 0; i < MqConstant.QUEUE_PARTITIONS; i++) {
            queues.add(new Queue(MqConstant.RESERVATION_QUEUE + i));
        }
        return queues;
    }

    @Bean
    public List<Binding> binding(DirectExchange reservationExchange, List<Queue> reservationQueues) {
        List<Binding> bindings = new ArrayList<>();
        for(int i = 0; i < reservationQueues.size(); i++) {
            bindings.add(BindingBuilder
                    .bind(reservationQueues.get(i))
                    .to(reservationExchange)
                    .with(MqConstant.RESERVATION_ROUTING_KEY + i));
        }
        return bindings;
    }

    @Bean
    public List<SimpleMessageListenerContainer> simpleMessageListenerContainers(CachingConnectionFactory connectionFactory, ReservationHandler reservationHandler) {
        List<SimpleMessageListenerContainer> containers = new ArrayList<>();
        for (int i = 0; i < MqConstant.QUEUE_PARTITIONS; i++) {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(MqConstant.RESERVATION_QUEUE + i);
            container.setMessageListener((Message message) -> {
                Long payload = ByteBuffer.wrap(message.getBody()).getLong();
                reservationHandler.process(payload);
            });
            container.setConcurrentConsumers(2);
            container.setMaxConcurrentConsumers(5);
            containers.add(container);
        }
        return containers;
    }

    @Bean
    public DirectExchange statusExchange() {
        return new DirectExchange(MqConstant.ORDER_EXCHANGE);
    }

    @Bean
    public Queue StatusQueue() {
        return new Queue(MqConstant.ORDER_QUEUE);
    }

    @Bean
    public Binding StatusBinding(DirectExchange statusExchange, Queue StatusQueue) {
        return BindingBuilder.bind(StatusQueue)
                .to(statusExchange)
                .with(MqConstant.ORDER_ROUTING_KEY);
    }
}
