package com.eyup.kervan.product.service;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.product.dto.ProductSummary;
import com.eyup.kervan.product.entity.Product;
import com.eyup.kervan.product.exception.InsufficientStockException;
import com.eyup.kervan.product.repository.ProductRepository;
import com.eyup.kervan.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductStockIT extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void adminAddsStock() throws Exception {
        Long id = createProduct(createCategory(), 5);

        mockMvc.perform(patch("/api/v1/products/{id}/stock", id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    void adminCannotMakeStockNegative() throws Exception {
        Long id = createProduct(createCategory(), 5);

        mockMvc.perform(patch("/api/v1/products/{id}/stock", id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(productService.getSummary(id).stock()).isEqualTo(5);
    }

    @Test
    void adjustingUnknownProductReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/products/{id}/stock", 999_999_999L)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void userCannotAdjustStock() throws Exception {
        Long id = createProduct(createCategory(), 5);

        mockMvc.perform(patch("/api/v1/products/{id}/stock", id)
                        .header("Authorization", bearerToken(2L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decreaseFailsWhenStockIsTooLow() throws Exception {
        Long id = createProduct(createCategory(), 2);

        assertThatThrownBy(() -> productService.decreaseStock(id, 3))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(productService.getSummary(id).stock()).isEqualTo(2);
    }

    @Test
    void increaseRestoresStock() throws Exception {
        Long id = createProduct(createCategory(), 5);

        productService.decreaseStock(id, 2);
        productService.increaseStock(id, 2);

        assertThat(productService.getSummary(id).stock()).isEqualTo(5);
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        int stock = 3;
        int buyers = 10;
        Long id = createProduct(createCategory(), stock);

        // All threads are released at the same moment; the conditional UPDATE lets exactly 3 win.
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        productService.decreaseStock(id, 1);
                        sold.incrementAndGet();
                    } catch (InsufficientStockException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(sold.get()).isEqualTo(stock);
        assertThat(rejected.get()).isEqualTo(buyers - stock);
        assertThat(productService.getSummary(id).stock()).isZero();
    }

    @Test
    void productEditDoesNotUndoConcurrentStockChange() throws Exception {
        Long id = createProduct(createCategory(), 5);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Product product = productRepository.findById(id).orElseThrow();  // loaded with stock 5

            // An order commits in another transaction between our load and our save.
            CompletableFuture.runAsync(() -> productService.decreaseStock(id, 2)).join();

            product.update("Yeni " + suffix(), "yeni-" + suffix(), null, new BigDecimal("19.90"), product.getCategory());
        });  // flush + commit here

        // Without @DynamicUpdate the UPDATE above would also write back the stock 5 it loaded.
        assertThat(productService.getSummary(id).stock()).isEqualTo(3);
    }

    @Test
    void stockChangeEvictsCachedDetail() throws Exception {
        Long id = createProduct(createCategory(), 5);

        mockMvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isOk());

        productService.decreaseStock(id, 1);

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(redisTemplate.hasKey("kervan:products::" + id)).isFalse());

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(4));
    }

    @Test
    void summariesAreLoadedInOneCallAndSkipMissingIds() throws Exception {
        Long categoryId = createCategory();
        Long a = createProduct(categoryId, 5);
        Long b = createProduct(categoryId, 7);

        Map<Long, ProductSummary> map = productService.findSummaries(List.of(a, b, 999_999_999L));

        assertThat(map).containsOnlyKeys(a, b);
        assertThat(map.get(b).stock()).isEqualTo(7);
        assertThat(map.get(a).price()).isEqualByComparingTo("19.90");
    }

    private String adminToken() {
        return bearerToken(1L, Role.ADMIN);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long createCategory() throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kategori %s\"}".formatted(suffix())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private Long createProduct(Long categoryId, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ürün %s","price":19.90,"stock":%d,"categoryId":%d}
                                """.formatted(suffix(), stock, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
