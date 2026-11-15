package com.eyup.kervan.product.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.product.dto.CategoryResponse;
import com.eyup.kervan.product.dto.ProductResponse;
import com.eyup.kervan.product.service.ProductService;
import com.eyup.kervan.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductCacheIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductService productService;

    @Test
    void detailIsCachedAndReadBackWithCorrectTypes() throws Exception {
        Long id = createProduct(createCategory());

        getProduct(id).andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(cacheKey(id))).isTrue();

        // Served from Redis now: BigDecimal, Instant and the nested record must survive the JSON round trip.
        ProductResponse cached = productService.getActiveById(id);
        assertThat(cached.price()).isEqualByComparingTo("19.90");
        assertThat(cached.createdAt()).isNotNull();
        assertThat(cached.category()).isInstanceOf(CategoryResponse.class);
    }

    @Test
    void updateEvictsCachedDetail() throws Exception {
        Long categoryId = createCategory();
        Long id = createProduct(categoryId);
        getProduct(id).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Güncel %s","price":42.00,"categoryId":%d,"version":0}
                                """.formatted(suffix(), categoryId)))
                .andExpect(status().isOk());

        awaitEvicted(id);
        getProduct(id).andExpect(jsonPath("$.price").value(42.0));
    }

    @Test
    void deleteEvictsCachedDetail() throws Exception {
        Long id = createProduct(createCategory());
        getProduct(id).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/products/{id}", id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        awaitEvicted(id);
        getProduct(id).andExpect(status().isNotFound());
    }

    @Test
    void categoryRenameEvictsCachedProducts() throws Exception {
        Long categoryId = createCategory();
        Long id = createProduct(categoryId);
        getProduct(id).andExpect(status().isOk());

        String newName = "Yeni Ad " + suffix();
        mockMvc.perform(put("/api/v1/categories/{id}", categoryId)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(newName)))
                .andExpect(status().isOk());

        awaitEvicted(id);
        getProduct(id).andExpect(jsonPath("$.category.name").value(newName));
    }

    // RedisCache.clear()/evict may finish just after the request returns, so wait briefly.
    private void awaitEvicted(Long id) {
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(redisTemplate.hasKey(cacheKey(id))).isFalse());
    }

    private static String cacheKey(Long id) {
        return "kervan:products::" + id;
    }

    private ResultActions getProduct(Long id) throws Exception {
        return mockMvc.perform(get("/api/v1/products/{id}", id));
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

    private Long createProduct(Long categoryId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ürün %s","price":19.90,"stock":5,"categoryId":%d}
                                """.formatted(suffix(), categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
