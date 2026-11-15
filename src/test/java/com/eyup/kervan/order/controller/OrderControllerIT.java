package com.eyup.kervan.order.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.cart.dto.AddCartItemRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    private Long placeOrder(Long userId, Long productId, int quantity) throws Exception {
        cartService.addItem(userId, new AddCartItemRequest(productId, quantity));
        String body = checkout(userId, ADDRESS)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
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

    @Test
    void listReturnsOnlyOwnOrdersNewestFirst() throws Exception {
        Long userA = newUser();
        Long userB = newUser();
        Long firstOrder = placeOrder(userA, createProduct("10.00", 5), 1);
        Long secondOrder = placeOrder(userA, createProduct("20.00", 5), 1);
        Long otherOrder = placeOrder(userB, createProduct("30.00", 5), 1);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", bearerToken(userA, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(secondOrder.intValue()))
                .andExpect(jsonPath("$.content[1].id").value(firstOrder.intValue()))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(otherOrder.intValue()))));
    }

    @Test
    void listIsEmptyForUserWithoutOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", bearerToken(newUser(), Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void listCanBeSortedByTotalAmount() throws Exception {
        Long user = newUser();
        Long expensive = placeOrder(user, createProduct("10.00", 10), 3);
        Long cheap = placeOrder(user, createProduct("10.00", 10), 1);

        mockMvc.perform(get(URI.create("/api/v1/orders?sort=totalAmount,asc"))
                        .header("Authorization", bearerToken(user, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(cheap.intValue()))
                .andExpect(jsonPath("$.content[0].totalAmount").value(10.0))
                .andExpect(jsonPath("$.content[1].id").value(expensive.intValue()))
                .andExpect(jsonPath("$.content[1].totalAmount").value(30.0));
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/orders?sort=userId")
                        .header("Authorization", bearerToken(newUser(), Role.USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void listRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerSeesOrderDetail() throws Exception {
        Long user = newUser();
        Long first = createProduct("10.50", 5);
        Long second = createProduct("3.00", 10);
        cartService.addItem(user, new AddCartItemRequest(first, 2));
        cartService.addItem(user, new AddCartItemRequest(second, 1));
        String body = checkout(user, ADDRESS)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = JsonPath.<Number>read(body, "$.id").longValue();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearerToken(user, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId").value(first.intValue()))
                .andExpect(jsonPath("$.items[1].productId").value(second.intValue()))
                .andExpect(jsonPath("$.totalAmount").value(24.0))
                .andExpect(jsonPath("$.shippingAddress.city").value("İstanbul"));
    }

    @Test
    void otherUserGets404ForForeignOrder() throws Exception {
        Long owner = newUser();
        Long orderId = placeOrder(owner, createProduct("10.00", 5), 1);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearerToken(newUser(), Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void adminSeesAnyOrder() throws Exception {
        Long owner = newUser();
        Long orderId = placeOrder(owner, createProduct("10.00", 5), 1);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", 999999999L)
                        .header("Authorization", bearerToken(newUser(), Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void listRunsTwoQueriesRegardlessOfOrderCount() throws Exception {
        Long user = newUser();
        placeOrder(user, createProduct("10.00", 10), 1);
        placeOrder(user, createProduct("20.00", 10), 1);
        placeOrder(user, createProduct("30.00", 10), 1);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            // page size 2 < 3 orders, so Spring Data really runs the count query (it skips it when the
            // first page is not full)
            mockMvc.perform(get("/api/v1/orders?size=2").header("Authorization", bearerToken(user, Role.USER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content", hasSize(2)));
            // one select for the page + one count query; an N+1 would add a select per order
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void detailLoadsOrderAndItemsInOneQuery() throws Exception {
        Long user = newUser();
        Long first = createProduct("10.00", 5);
        Long second = createProduct("20.00", 5);
        cartService.addItem(user, new AddCartItemRequest(first, 1));
        cartService.addItem(user, new AddCartItemRequest(second, 1));
        String body = checkout(user, ADDRESS)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = JsonPath.<Number>read(body, "$.id").longValue();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearerToken(user, Role.USER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(2)));
            // the order and its items come from a single select; a lazy items collection would add another
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
