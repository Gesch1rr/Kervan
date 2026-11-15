package com.eyup.kervan.order.service;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.cart.service.CartService;
import com.eyup.kervan.order.dto.CreateOrderRequest;
import com.eyup.kervan.order.dto.ShippingAddress;
import com.eyup.kervan.order.exception.EmptyCartException;
import com.eyup.kervan.order.exception.OrderNotCancellableException;
import com.eyup.kervan.product.exception.InsufficientStockException;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderConcurrencyIT extends AbstractIntegrationTest {

    private static final CreateOrderRequest ORDER = new CreateOrderRequest(
            new ShippingAddress("Atatürk Cad. No:1", "İstanbul", "34000", "Türkiye"));

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void lastItemsAreNeverOversold() throws Exception {
        Long product = createProduct(3);
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Long user = newUser();
            cartService.addItem(user, new AddCartItemRequest(product, 1));
            users.add(user);
        }

        List<Throwable> failures = runAtOnce(users.stream()
                .<Callable<Object>>map(user -> () -> orderService.placeOrder(user, ORDER))
                .toList());

        // 6 buyers, 3 items: exactly 3 orders, the rest are told there is not enough stock
        assertThat(failures).hasSize(3).allMatch(InsufficientStockException.class::isInstance);
        assertThat(stockOf(product)).isZero();
        assertThat(orderLinesFor(product)).isEqualTo(3);
    }

    // Half the buyers have A before B in their cart, the other half B before A. Without sorting the lines
    // by product id, two checkouts could each lock one row and wait for the other: PostgreSQL would then
    // abort one of them with "deadlock detected".
    @Test
    void oppositeCartOrderDoesNotDeadlock() throws Exception {
        Long a = createProduct(100);
        Long b = createProduct(100);
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Long user = newUser();
            List<Long> order = i % 2 == 0 ? List.of(a, b) : List.of(b, a);
            for (Long product : order) {
                cartService.addItem(user, new AddCartItemRequest(product, 1));
            }
            users.add(user);
        }

        List<Throwable> failures = runAtOnce(users.stream()
                .<Callable<Object>>map(user -> () -> orderService.placeOrder(user, ORDER))
                .toList());

        assertThat(failures).isEmpty();
        assertThat(stockOf(a)).isEqualTo(90);
        assertThat(stockOf(b)).isEqualTo(90);
    }

    // A double click (or two devices) must not turn one cart into two orders.
    @Test
    void sameCartCannotBeOrderedTwice() throws Exception {
        Long product = createProduct(10);
        Long user = newUser();
        cartService.addItem(user, new AddCartItemRequest(product, 2));

        List<Throwable> failures = runAtOnce(List.<Callable<Object>>of(
                () -> orderService.placeOrder(user, ORDER),
                () -> orderService.placeOrder(user, ORDER),
                () -> orderService.placeOrder(user, ORDER)));

        // the losers either collided with the winner (409) or came after it and found the cart empty
        assertThat(failures).hasSize(2).allMatch(failure ->
                failure instanceof OptimisticLockingFailureException || failure instanceof EmptyCartException);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where user_id = ?", Integer.class, user))
                .isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    // A double click on "cancel" must not put the same items back twice. Without the conditional
    // UPDATE several cancels could each put the 4 items back.
    @Test
    void concurrentCancelsRestoreStockOnce() throws Exception {
        Long product = createProduct(10);
        Long user = newUser();
        cartService.addItem(user, new AddCartItemRequest(product, 4));
        Long orderId = orderService.placeOrder(user, ORDER).id();
        assertThat(stockOf(product)).isEqualTo(6);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> orderService.cancel(orderId, user));
        }
        List<Throwable> failures = runAtOnce(tasks);

        assertThat(failures).hasSize(4).allMatch(OrderNotCancellableException.class::isInstance);
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");
    }

    // Starts all tasks at the same moment and returns the exceptions of the ones that failed.
    private static List<Throwable> runAtOnce(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    task.call();
                } catch (Exception e) {
                    failures.add(e);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return failures;
    }

    private Long newUser() {
        return userService.create("order-" + UUID.randomUUID() + "@test.local", "password123", "Order User", Role.USER).id();
    }

    private int stockOf(Long productId) {
        return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    private int orderLinesFor(Long productId) {
        return jdbcTemplate.queryForObject("select count(*) from order_items where product_id = ?", Integer.class, productId);
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
