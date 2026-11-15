package com.eyup.kervan.product.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.product.dto.CategoryResponse;
import com.eyup.kervan.product.service.CategoryService;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerIT extends AbstractIntegrationTest {

    private static final String CACHE_KEY = "kervan:categories::all";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CategoryService categoryService;

    private String adminToken() {
        return bearerToken(1L, Role.ADMIN);
    }

    // the DB is shared with other tests, so names must be unique
    private static String uniqueName() {
        return "Kategori " + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions create(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)));
    }

    private Long createAndGetId(String name) throws Exception {
        String body = create(name)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    @Test
    void listIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCreatesCategoryWithSlug() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String name = "Ev & Yaşam " + suffix;

        create(name)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.slug").value("ev-yasam-" + suffix));
    }

    @Test
    void userCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearerToken(2L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden %s\"}".formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void duplicateNameIsRejected() throws Exception {
        String name = uniqueName();

        create(name).andExpect(status().isCreated());

        create(name)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        create("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateRenamesCategory() throws Exception {
        Long id = createAndGetId(uniqueName());

        mockMvc.perform(put("/api/v1/categories/{id}", id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yeni %s\"}".formatted(uniqueName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", startsWith("yeni-")));
    }

    @Test
    void updateUnknownCategoryReturns404() throws Exception {
        mockMvc.perform(put("/api/v1/categories/999999999")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yeni %s\"}".formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void deleteRemovesCategory() throws Exception {
        Long id = createAndGetId(uniqueName());

        mockMvc.perform(delete("/api/v1/categories/{id}", id)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id.intValue()))));
    }

    @Test
    void listIsServedFromCacheAndEvictedOnWrite() throws Exception {
        createAndGetId(uniqueName());  // the list must not be empty, also when this test runs alone

        String first = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(redisTemplate.hasKey(CACHE_KEY)).isTrue();

        String second = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);

        // The HTTP output would look the same even if Redis returned Maps, so check the Java types
        // of the cached list directly: without type info in the cache this fails with ClassCastException.
        assertThat(categoryService.list())
                .isNotEmpty()
                .allSatisfy(category -> assertThat(category).isInstanceOf(CategoryResponse.class))
                .first()
                .extracting(CategoryResponse::name)
                .isNotNull();

        String name = uniqueName();
        createAndGetId(name);

        // RedisCache.clear() may finish slightly after the request returns, so wait briefly instead of asserting at once.
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(redisTemplate.hasKey(CACHE_KEY)).isFalse());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(name)));
    }
}
