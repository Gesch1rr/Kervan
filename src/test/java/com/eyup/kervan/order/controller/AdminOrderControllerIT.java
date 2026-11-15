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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOrderControllerIT extends AbstractIntegrationTest {

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

    private ResultActions changeStatus(String token, Long orderId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status)));
    }

    private int stockOf(Long productId) {
        return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    @Test
    void adminMovesOrderThroughConfirmedShippedDelivered() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 1);

        changeStatus(adminToken(), orderId, "CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        changeStatus(adminToken(), orderId, "SHIPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        changeStatus(adminToken(), orderId, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(statusOf(orderId)).isEqualTo("DELIVERED");
    }

    @Test
    void skippingAStepIsRejected() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 1);

        changeStatus(adminToken(), orderId, "SHIPPED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(statusOf(orderId)).isEqualTo("PENDING");
    }

    @Test
    void nothingGoesBackToPending() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 1);

        changeStatus(adminToken(), orderId, "CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        changeStatus(adminToken(), orderId, "PENDING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
    }

    @Test
    void adminCancelOfConfirmedRestoresStock() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 2);
        assertThat(stockOf(product)).isEqualTo(3);

        changeStatus(adminToken(), orderId, "CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        changeStatus(adminToken(), orderId, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockOf(product)).isEqualTo(5);
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void deliveredOrderCannotBeCancelled() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 2);
        assertThat(stockOf(product)).isEqualTo(3);

        changeStatus(adminToken(), orderId, "CONFIRMED").andExpect(status().isOk());
        changeStatus(adminToken(), orderId, "SHIPPED").andExpect(status().isOk());
        changeStatus(adminToken(), orderId, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        changeStatus(adminToken(), orderId, "CANCELLED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(statusOf(orderId)).isEqualTo("DELIVERED");
        assertThat(stockOf(product)).isEqualTo(3);
    }

    @Test
    void userCannotCancelShippedOrder() throws Exception {
        Long owner = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(owner, product, 2);

        changeStatus(adminToken(), orderId, "CONFIRMED").andExpect(status().isOk());
        changeStatus(adminToken(), orderId, "SHIPPED").andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", bearerToken(owner, Role.USER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));

        assertThat(statusOf(orderId)).isEqualTo("SHIPPED");
    }

    @Test
    void unknownStatusValueIsRejected() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 1);

        changeStatus(adminToken(), orderId, "FLYING")
                .andExpect(status().isBadRequest());

        assertThat(statusOf(orderId)).isEqualTo("PENDING");
    }

    @Test
    void unknownOrderIs404() throws Exception {
        changeStatus(adminToken(), 999999999L, "CONFIRMED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 10);
        Long first = placeOrder(user, product, 1);
        Long second = placeOrder(user, product, 1);

        changeStatus(adminToken(), second, "CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", adminToken())
                        .param("status", "CONFIRMED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(second.intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(first.intValue()))))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("CONFIRMED"))));
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", adminToken())
                        .param("sort", "userId"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void nonAdminGets403() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        Long orderId = placeOrder(user, product, 1);

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", bearerToken(user, Role.USER)))
                .andExpect(status().isForbidden());

        changeStatus(bearerToken(user, Role.USER), orderId, "CONFIRMED")
                .andExpect(status().isForbidden());

        assertThat(statusOf(orderId)).isEqualTo("PENDING");
    }

    @Test
    void adminEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
    }
}
