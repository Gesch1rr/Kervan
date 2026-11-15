package com.eyup.kervan.order.event;

import com.eyup.kervan.order.repository.ProcessedEventRepository;
import com.eyup.kervan.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final OrderService orderService;

    // The marker and the confirmation share one transaction, so if confirm throws, the marker is rolled
    // back too and a retry handles the message again. After 3 failed attempts
    // (spring.rabbitmq.listener.simple.retry) the message is rejected without requeue and goes to the DLQ.
    @RabbitListener(id = "orderCreated", queues = OrderRabbitConfig.QUEUE)
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (processedEventRepository.insertIfAbsent(event.eventId(), Instant.now()) == 0) {
            log.info("Skipping duplicate event {}", event.eventId());
            return;
        }

        orderService.confirm(event.orderId());
    }
}
