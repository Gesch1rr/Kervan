package com.eyup.kervan.order.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
import com.eyup.kervan.cart.service.CartService;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderCancelIT extends AbstractIntegrationTest {

    private static final String ADDRESS = """
            {"shippingAddress":{"line1":"Atatürk Cad. No:1","city":"İstanbul","postalCode":"34000","country":"Türkiye"}}
            """;

    @Autowired
    private UserService userService;

    @Autowired
    private CartService cartService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken() {
        return bearerToken(1L, Role.ADMIN);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long newUser() {
        return userService.create("cancel-" + UUID.randomUUID() + "@test.local", "password123", "Cancel User", Role.USER).id();
    }

    private Long createProduct(String price, int stock) throws Exception {
        String category = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kategori %s\"}".formatted(suffix())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ürün %s\",\"price\":%s,\"stock\":%d,\"categoryId\":%d}".formatted(
                                suffix(), price, stock, JsonPath.<Number>read(category, "$.id").longValue())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(product, "$.id").longValue();
    }

    private ResultActions checkout(Long userId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", bearerToken(userId, Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long placeOrder(Long userId, Long productId, int quantity) throws Exception {
        cartService.addItem(userId, new AddCartItemRequest(productId, quantity));
        String body = checkout(userId, ADDRESS)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private ResultActions cancel(Long userId, Long orderId) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                .header("Authorization", bearerToken(userId, Role.USER)));
    }

    private int stockOf(Long productId) {
        return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    @Test
    void ownerCancelsPendingOrderAndStockIsRestored() throws Exception {
        Long user = newUser();
        Long first = createProduct("10.50", 5);
        Long second = createProduct("3.00", 10);
        cartService.addItem(user, new AddCartItemRequest(first, 2));
        cartService.addItem(user, new AddCartItemRequest(second, 3));
        String body = checkout(user, ADDRESS)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = JsonPath.<Number>read(body, "$.id").longValue();
        assertThat(stockOf(first)).isEqualTo(3);
        assertThat(stockOf(second)).isEqualTo(7);

        cancel(user, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockOf(first)).isEqualTo(5);
        assertThat(stockOf(second)).isEqualTo(10);
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void cancelledOrderCannotBeCancelledAgain() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 2);
        assertThat(stockOf(product)).isEqualTo(3);

        cancel(user, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockOf(product)).isEqualTo(5);

        cancel(user, orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        assertThat(stockOf(product)).isEqualTo(5);
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void otherUsersOrderLooksMissing() throws Exception {
        Long owner = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(owner, product, 2);
        assertThat(stockOf(product)).isEqualTo(3);

        cancel(newUser(), orderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(statusOf(orderId)).isEqualTo("PENDING");
        assertThat(stockOf(product)).isEqualTo(3);
    }

    @Test
    void unknownOrderIs404() throws Exception {
        cancel(newUser(), 999999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelRequiresToken() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", 1L))
                .andExpect(status().isUnauthorized());
    }
}
