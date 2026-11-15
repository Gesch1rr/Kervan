package com.eyup.kervan.order.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitConfig {

    public static final String EXCHANGE = "kervan.orders";
    public static final String ROUTING_KEY = "order.created";
    public static final String QUEUE = "kervan.order.created";
    public static final String DLX = "kervan.orders.dlx";
    public static final String DLQ = "kervan.order.created.dlq";

    @Bean
    public DirectExchange ordersExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange ordersDeadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue orderCreatedQueue() {
        // rejected messages go to the DLX with their original routing key
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
    }

    @Bean
    public Queue orderCreatedDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(ordersExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding orderCreatedDeadLetterBinding() {
        return BindingBuilder.bind(orderCreatedDeadLetterQueue()).to(ordersDeadLetterExchange()).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // JSON instead of Java serialization; only classes from the event package may be created from the
        // type header (trusted packages must match exactly, sub-packages are not included).
        // Boot uses this bean for both RabbitTemplate and listeners.
        return new JacksonJsonMessageConverter("com.eyup.kervan.order.event");
    }
}
