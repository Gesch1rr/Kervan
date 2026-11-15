package com.eyup.kervan.product.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        return bearerToken(1L, Role.ADMIN);
    }

    // the DB is shared with other tests, so names must be unique
    private static String uniqueName() {
        return "Urun " + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long createCategory() throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kategori %s\"}".formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ResultActions createProduct(String name, String price, Long categoryId) throws Exception {
        return mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"description\":\"Açıklama\",\"price\":%s,\"stock\":5,\"categoryId\":%d}"
                        .formatted(name, price, categoryId)));
    }

    private Long createProductAndGetId(String name, String price, Long categoryId) throws Exception {
        String body = createProduct(name, price, categoryId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ResultActions updateProduct(Long id, String name, String price, Long categoryId, long version) throws Exception {
        return mockMvc.perform(put("/api/v1/products/{id}", id)
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"description\":\"Yeni\",\"price\":%s,\"categoryId\":%d,\"version\":%d}"
                        .formatted(name, price, categoryId, version)));
    }

    @Test
    void adminCreatesProduct() throws Exception {
        Long categoryId = createCategory();
        String name = uniqueName();

        createProduct(name, "19.9", categoryId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.slug", startsWith("urun-")))
                .andExpect(jsonPath("$.price").value(19.9))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.category.id").value(categoryId.intValue()))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void userCannotCreateProduct() throws Exception {
        Long categoryId = createCategory();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearerToken(2L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":10.00,\"stock\":1,\"categoryId\":%d}"
                                .formatted(uniqueName(), categoryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createWithUnknownCategoryReturns404() throws Exception {
        createProduct(uniqueName(), "10.00", 999999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void negativePriceIsRejected() throws Exception {
        createProduct(uniqueName(), "-1", createCategory())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateNameIsRejected() throws Exception {
        Long categoryId = createCategory();
        String name = uniqueName();

        createProduct(name, "10.00", categoryId).andExpect(status().isCreated());

        createProduct(name, "10.00", categoryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_EXISTS"));
    }

    @Test
    void productIsPublic() throws Exception {
        Long categoryId = createCategory();
        String name = uniqueName();
        Long id = createProductAndGetId(name, "10.00", categoryId);

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updateWithCurrentVersionSucceeds() throws Exception {
        Long categoryId = createCategory();
        Long id = createProductAndGetId(uniqueName(), "10.00", categoryId);

        updateProduct(id, uniqueName(), "25.50", categoryId, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(25.5))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        Long categoryId = createCategory();
        Long id = createProductAndGetId(uniqueName(), "10.00", categoryId);
        String name = uniqueName();

        updateProduct(id, name, "25.50", categoryId, 0).andExpect(status().isOk());

        updateProduct(id, name, "25.50", categoryId, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void deletedProductIsHidden() throws Exception {
        Long categoryId = createCategory();
        Long id = createProductAndGetId(uniqueName(), "10.00", categoryId);

        mockMvc.perform(delete("/api/v1/products/{id}", id)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        Long categoryId = createCategory();
        createProductAndGetId(uniqueName(), "10.00", categoryId);

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }
}
