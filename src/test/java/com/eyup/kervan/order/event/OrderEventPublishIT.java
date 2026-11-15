package com.eyup.kervan.order.event;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.cart.service.CartService;
import com.eyup.kervan.order.dto.CreateOrderRequest;
import com.eyup.kervan.order.dto.OrderResponse;
import com.eyup.kervan.order.dto.ShippingAddress;
import com.eyup.kervan.order.service.OrderService;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderEventPublishIT extends AbstractIntegrationTest {

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
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void purgeQueue() {
        amqpAdmin.purgeQueue(OrderRabbitConfig.QUEUE, false);
    }

    @Test
    void checkoutPublishesEventAfterCommit() throws Exception {
        Long product = createProduct(5);
        Long user = newUser();
        cartService.addItem(user, new AddCartItemRequest(product, 2));

        OrderResponse order = orderService.placeOrder(user, ORDER);

        Object received = rabbitTemplate.receiveAndConvert(OrderRabbitConfig.QUEUE, 5000);
        assertThat(received).isInstanceOf(OrderCreatedEvent.class);
        OrderCreatedEvent event = (OrderCreatedEvent) received;
        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.userId()).isEqualTo(user);
        assertThat(event.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    void rolledBackCheckoutPublishesNothing() throws Exception {
        Long product = createProduct(5);
        Long user = newUser();
        cartService.addItem(user, new AddCartItemRequest(product, 1));

        // The rollback happens after publishEvent; with a plain @EventListener the message would already be in the queue.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orderService.placeOrder(user, ORDER);
            status.setRollbackOnly();
        });

        assertThat(rabbitTemplate.receive(OrderRabbitConfig.QUEUE, 1000)).isNull();
        Long orders = jdbcTemplate.queryForObject(
                "select count(*) from orders where user_id = ?", Long.class, user);
        assertThat(orders).isZero();
    }

    private Long newUser() {
        return userService.create("order-" + UUID.randomUUID() + "@test.local", "password123", "Order User", Role.USER).id();
    }

    private Long createProduct(int stock) throws Exception {
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
    }
}
