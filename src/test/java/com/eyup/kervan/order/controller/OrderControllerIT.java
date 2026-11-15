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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerIT extends AbstractIntegrationTest {

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
        return userService.create("order-" + UUID.randomUUID() + "@test.local", "password123", "Order User", Role.USER).id();
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

    private int stockOf(Long productId) {
        return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    private int orderCount(Long userId) {
        return jdbcTemplate.queryForObject("select count(*) from orders where user_id = ?", Integer.class, userId);
    }

    @Test
    void checkoutRequiresToken() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ADDRESS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutCreatesOrderTakesStockAndEmptiesCart() throws Exception {
        Long user = newUser();
        Long first = createProduct("10.50", 5);
        Long second = createProduct("3.00", 10);
        cartService.addItem(user, new AddCartItemRequest(first, 2));
        cartService.addItem(user, new AddCartItemRequest(second, 3));

        checkout(user, ADDRESS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(30.0))
                .andExpect(jsonPath("$.shippingAddress.city").value("İstanbul"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId").value(first.intValue()))
                .andExpect(jsonPath("$.items[0].productName").isString())
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.5))
                .andExpect(jsonPath("$.items[0].lineTotal").value(21.0))
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(stockOf(first)).isEqualTo(3);
        assertThat(stockOf(second)).isEqualTo(7);
        assertThat(cartService.getCart(user).items()).isEmpty();
    }

    @Test
    void orderKeepsThePriceItWasPlacedAt() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        cartService.addItem(user, new AddCartItemRequest(product, 1));
        checkout(user, ADDRESS).andExpect(status().isCreated());

        jdbcTemplate.update("update products set price = 99.00 where id = ?", product);

        assertThat(jdbcTemplate.queryForObject(
                "select unit_price from order_items where product_id = ?", BigDecimal.class, product))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void emptyCartCannotBeCheckedOut() throws Exception {
        checkout(newUser(), ADDRESS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    void missingAddressIsRejected() throws Exception {
        Long user = newUser();
        cartService.addItem(user, new AddCartItemRequest(createProduct("10.00", 5), 1));

        checkout(user, "{}").andExpect(status().isBadRequest());
        checkout(user, "{\"shippingAddress\":{\"line1\":\" \",\"city\":\"Ankara\",\"postalCode\":\"06000\",\"country\":\"TR\"}}")
                .andExpect(status().isBadRequest());
        assertThat(orderCount(user)).isZero();
    }

    @Test
    void deactivatedProductStopsCheckout() throws Exception {
        Long user = newUser();
        Long product = createProduct("10.00", 5);
        cartService.addItem(user, new AddCartItemRequest(product, 1));
        mockMvc.perform(delete("/api/v1/products/{id}", product).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        checkout(user, ADDRESS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
        assertThat(orderCount(user)).isZero();
    }

    // The first line's stock is taken before the second line fails; the rollback must give it back.
    @Test
    void failedCheckoutRollsBackEverything() throws Exception {
        Long user = newUser();
        Long first = createProduct("10.00", 5);   // created first, so it has the lower id and is decreased first
        Long second = createProduct("10.00", 5);
        cartService.addItem(user, new AddCartItemRequest(first, 2));
        cartService.addItem(user, new AddCartItemRequest(second, 4));
        // someone else buys most of the second product after it was added to the cart
        mockMvc.perform(patch("/api/v1/products/{id}/stock", second)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-3}"))
                .andExpect(status().isOk());

        checkout(user, ADDRESS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(stockOf(first)).isEqualTo(5);
        assertThat(stockOf(second)).isEqualTo(2);
        assertThat(orderCount(user)).isZero();
        assertThat(cartService.getCart(user).items()).hasSize(2);
    }
}
