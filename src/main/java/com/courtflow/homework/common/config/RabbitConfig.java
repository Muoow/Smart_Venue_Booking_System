package com.courtflow.homework.common.config;

import com.courtflow.homework.common.constant.MqConstant;
import com.courtflow.homework.handler.ReservationHandler;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Declarables reservationDeclarables() {
        List<Declarable> declarables = new ArrayList<>();

        DirectExchange exchange = new DirectExchange(MqConstant.RESERVATION_EXCHANGE);
        declarables.add(exchange);

        for (int i = 0; i < MqConstant.QUEUE_PARTITIONS; i++) {
            String queueName = MqConstant.RESERVATION_QUEUE + i;
            String routingKey = MqConstant.RESERVATION_ROUTING_KEY + i;

            Queue queue = new Queue(queueName, true); // durable
            Binding binding = BindingBuilder
                    .bind(queue)
                    .to(exchange)
                    .with(routingKey);

            declarables.add(queue);
            declarables.add(binding);
        }

        return new Declarables(declarables);
    }

    @Bean
    public List<SimpleMessageListenerContainer> reservationListeners(
            CachingConnectionFactory connectionFactory,
            ReservationHandler reservationHandler) {

        List<SimpleMessageListenerContainer> containers = new ArrayList<>();

        for (int i = 0; i < MqConstant.QUEUE_PARTITIONS; i++) {
            String queueName = MqConstant.RESERVATION_QUEUE + i;

            SimpleMessageListenerContainer container =
                    new SimpleMessageListenerContainer(connectionFactory);

            container.setQueueNames(queueName);

            container.setMessageListener((Message message) -> {
                byte[] body = message.getBody();

                if (body.length != 8) {
                    throw new IllegalArgumentException("invalid payload length: " + body.length);
                }

                long reservationId = ByteBuffer.wrap(body).getLong();

                reservationHandler.process(reservationId);
            });

            container.setConcurrentConsumers(1);
            container.setMaxConcurrentConsumers(1);
            container.setAcknowledgeMode(AcknowledgeMode.AUTO);
            container.setDefaultRequeueRejected(false);

            container.start();

            containers.add(container);
        }

        return containers;
    }

    @Bean
    public DirectExchange statusExchange() {
        return new DirectExchange(MqConstant.ORDER_EXCHANGE);
    }

    @Bean
    public Queue statusQueue() {
        return new Queue(MqConstant.ORDER_QUEUE);
    }

    @Bean
    public Binding statusBinding(DirectExchange statusExchange, Queue StatusQueue) {
        return BindingBuilder.bind(StatusQueue)
                .to(statusExchange)
                .with(MqConstant.ORDER_ROUTING_KEY);
    }
}
