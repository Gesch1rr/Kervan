package com.eyup.kervan.order.event;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.cart.service.CartService;
import com.eyup.kervan.order.dto.CreateOrderRequest;
import com.eyup.kervan.order.dto.ShippingAddress;
import com.eyup.kervan.order.service.OrderService;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderCreatedConsumerIT extends AbstractIntegrationTest {

    private static final CreateOrderRequest ORDER = new CreateOrderRequest(
            new ShippingAddress("Atatürk Cad. No:1", "İstanbul", "34000", "Türkiye"));

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry registry;

    @BeforeEach
    void startListener() {
        amqpAdmin.purgeQueue(OrderRabbitConfig.QUEUE, false);
        amqpAdmin.purgeQueue(OrderRabbitConfig.DLQ, false);
        listener().start();
    }

    @AfterEach
    void stopListener() {
        // other test classes expect freshly placed orders to stay PENDING
        listener().stop();
    }

    @Test
    void consumerConfirmsPlacedOrder() {
        Long order = placeOrder(newUser());

        await().atMost(Duration.ofSeconds(10)).until(() -> "CONFIRMED".equals(statusOf(order)));
    }

    @Test
    void duplicateEventIsHandledOnce() {
        Long user = newUser();
        Long order = placeOrder(user);
        await().atMost(Duration.ofSeconds(10)).until(() -> "CONFIRMED".equals(statusOf(order)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), order, user, new BigDecimal("10.00"), Instant.now());
        send(event);
        waitUntilQueueDrained();

        Long processed = jdbcTemplate.queryForObject(
                "select count(*) from processed_events where event_id = ?", Long.class, event.eventId());
        assertThat(processed).isEqualTo(1L);

        // if the duplicate were handled again, confirm() would move the order to CONFIRMED
        jdbcTemplate.update("update orders set status = 'PENDING' where id = ?", order);
        send(event);
        waitUntilQueueDrained();

        assertThat(statusOf(order)).isEqualTo("PENDING");
    }

    @Test
    void cancelledOrderStaysCancelled() {
        listener().stop();
        Long user = newUser();
        Long order = placeOrder(user);
        orderService.cancel(order, user);
        listener().start();

        waitUntilQueueDrained();

        assertThat(statusOf(order)).isEqualTo("CANCELLED");
    }

    @Test
    void eventForUnknownOrderEndsInDlq() {
        UUID eventId = UUID.randomUUID();
        send(new OrderCreatedEvent(eventId, 999999999L, 1L, BigDecimal.TEN, Instant.now()));

        AtomicReference<Message> dead = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                (dead.updateAndGet(previous -> previous != null
                        ? previous
                        : rabbitTemplate.receive(OrderRabbitConfig.DLQ, 100))) != null);

        // the processed_events marker was rolled back together with the failed confirm
        Long processed = jdbcTemplate.queryForObject(
                "select count(*) from processed_events where event_id = ?", Long.class, eventId);
        assertThat(processed).isZero();
    }

    @Test
    void unreadableMessageEndsInDlq() {
        // the type header names a class outside the trusted package, so the converter refuses it
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("__TypeId__", "java.net.URL");
        rabbitTemplate.send(OrderRabbitConfig.EXCHANGE, OrderRabbitConfig.ROUTING_KEY,
                new Message("{\"x\":1}".getBytes(StandardCharsets.UTF_8), props));

        AtomicReference<Message> dead = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                (dead.updateAndGet(previous -> previous != null
                        ? previous
                        : rabbitTemplate.receive(OrderRabbitConfig.DLQ, 100))) != null);
    }

    private MessageListenerContainer listener() {
        return registry.getListenerContainer("orderCreated");
    }

    private Long placeOrder(Long user) {
        Long product = createProduct(10);
        cartService.addItem(user, new AddCartItemRequest(product, 1));
        return orderService.placeOrder(user, ORDER).id();
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    private void send(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(OrderRabbitConfig.EXCHANGE, OrderRabbitConfig.ROUTING_KEY, event);
    }

    private void waitUntilQueueDrained() {
        // one consumer thread handles messages in order, so once the sentinel is confirmed
        // every message sent before it has been handled
        Long sentinel = placeOrder(newUser());
        await().atMost(Duration.ofSeconds(10)).until(() -> "CONFIRMED".equals(statusOf(sentinel)));
    }

    private Long newUser() {
        return userService.create("consumer-" + UUID.randomUUID() + "@test.local", "password123", "Consumer User", Role.USER).id();
    }

    private Long createProduct(int stock) {
        try {
            String admin = bearerToken(1L, Role.ADMIN);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String category = mockMvc.perform(post("/api/v1/categories")
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Kategori %s\"}".formatted(suffix)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String product = mockMvc.perform(post("/api/v1/products")
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Ürün %s\",\"price\":10.00,\"stock\":%d,\"categoryId\":%d}".formatted(
                                    suffix, stock, JsonPath.<Number>read(category, "$.id").longValue())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.<Number>read(product, "$.id").longValue();
        } catch (Exception e) {
            throw new IllegalStateException("could not create product", e);
        }
    }
}
