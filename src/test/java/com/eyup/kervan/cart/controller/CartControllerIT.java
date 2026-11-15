package com.eyup.kervan.cart.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.cart.dto.CartResponse;
import com.eyup.kervan.cart.service.CartService;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CartService cartService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String adminToken() {
        return bearerToken(1L, Role.ADMIN);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long newUser() {
        return userService.create("cart-" + UUID.randomUUID() + "@test.local", "password123", "Cart User", Role.USER).id();
    }

    private String token(Long userId) {
        return bearerToken(userId, Role.USER);
    }

    // the DB is shared with other tests, so names must be unique
    private Long createCategory() throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kategori %s\"}".formatted(suffix())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private Long createProduct(Long categoryId, String price, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ürün %s\",\"description\":\"Açıklama\",\"price\":%s,\"stock\":%d,\"categoryId\":%d}"
                                .formatted(suffix(), price, stock, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ResultActions addItem(Long userId, Long productId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/items")
                .header("Authorization", token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)));
    }

    private ResultActions getCart(Long userId) throws Exception {
        return mockMvc.perform(get("/api/v1/cart")
                .header("Authorization", token(userId)));
    }

    @Test
    void cartRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newUserHasEmptyCart() throws Exception {
        getCart(newUser())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void addedItemShowsProductDetailsAndTotals() throws Exception {
        Long user = newUser();
        Long product = createProduct(createCategory(), "19.90", 5);

        addItem(user, product, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(product.intValue()))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19.9))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(39.8))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.totalAmount").value(39.8));
    }

    @Test
    void addingSameProductAgainMergesQuantity() throws Exception {
        Long user = newUser();
        Long product = createProduct(createCategory(), "10.00", 5);

        addItem(user, product, 1).andExpect(status().isOk());

        addItem(user, product, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void cannotAddMoreThanStock() throws Exception {
        Long user = newUser();
        Long product = createProduct(createCategory(), "10.00", 5);

        addItem(user, product, 3).andExpect(status().isOk());

        addItem(user, product, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        getCart(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        addItem(newUser(), 999999999L, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void deletedProductCannotBeAdded() throws Exception {
        Long user = newUser();
        Long product = createProduct(createCategory(), "10.00", 5);

        mockMvc.perform(delete("/api/v1/products/{id}", product)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        addItem(user, product, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void quantityMustBeBetween1And99() throws Exception {
        Long user = newUser();
        Long product = createProduct(createCategory(), "10.00", 100);

        addItem(user, product, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        addItem(user, product, 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void cartsArePerUser() throws Exception {
        Long userA = newUser();
        Long userB = newUser();
        Long product = createProduct(createCategory(), "10.00", 5);

        addItem(userA, product, 1).andExpect(status().isOk());

        getCart(userB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void cartLoadsWithTwoQueries() throws Exception {
        Long user = newUser();
        Long cat = createCategory();
        Long first = createProduct(cat, "10.00", 5);
        Long second = createProduct(cat, "20.00", 5);
        Long third = createProduct(cat, "30.00", 5);
        cartService.addItem(user, new AddCartItemRequest(first, 1));
        cartService.addItem(user, new AddCartItemRequest(second, 1));
        cartService.addItem(user, new AddCartItemRequest(third, 1));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            CartResponse cart = cartService.getCart(user);
            assertThat(cart.items()).hasSize(3);
            // cart + items in one query, all product summaries in one more; N+1 would add a query per line
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
