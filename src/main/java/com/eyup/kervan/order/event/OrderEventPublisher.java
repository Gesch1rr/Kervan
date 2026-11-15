package com.eyup.kervan.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: a rolled-back checkout never announces an order that does not exist.
    // The order is already saved at this point, so a broker outage must not turn the checkout into an error;
    // the event is lost then (known gap, an outbox table would fix it).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(OrderRabbitConfig.EXCHANGE, OrderRabbitConfig.ROUTING_KEY, event);
        } catch (AmqpException e) {
            log.error("Could not publish {}", event, e);
        }
    }
}
