package com.eyup.kervan.cart.service;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartConcurrencyIT extends AbstractIntegrationTest {

    private static final int DEVICES = 8;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    @Test
    void concurrentChangesToTheSameCartAreNeverLost() throws Exception {
        Long userId = userService.create("cart-" + UUID.randomUUID() + "@test.local", "password123", "Cart User", Role.USER).id();
        Long productId = createProduct(50);
        cartService.addItem(userId, new AddCartItemRequest(productId, 1));

        ExecutorService pool = Executors.newFixedThreadPool(DEVICES);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < DEVICES; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    cartService.addItem(userId, new AddCartItemRequest(productId, 1));
                    accepted.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    rejected.incrementAndGet();  // the API turns this into 409; the client reloads and retries
                }
                return null;
            }));
        }
        start.countDown();  // all "devices" press the button at the same moment
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        int quantity = cartService.getCart(userId).items().getFirst().quantity();
        // Every accepted add must be in the cart; a lost update would leave the quantity lower.
        assertThat(quantity).isEqualTo(1 + accepted.get());
        assertThat(accepted.get() + rejected.get()).isEqualTo(DEVICES);
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
                        .content("""
                                {"name":"Ürün %s","price":10.00,"stock":%d,"categoryId":%d}
                                """.formatted(suffix, stock, JsonPath.<Number>read(category, "$.id").longValue())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(product, "$.id").longValue();
    }
}
