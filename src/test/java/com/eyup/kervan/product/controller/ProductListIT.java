package com.eyup.kervan.product.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.common.dto.PageResponse;
import com.eyup.kervan.product.dto.ProductFilter;
import com.eyup.kervan.product.dto.ProductResponse;
import com.eyup.kervan.product.service.ProductService;
import com.eyup.kervan.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductListIT extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String adminToken() {
        return bearerToken(1L, Role.ADMIN);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
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

    private Long createProduct(String name, Long categoryId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"description\":\"Açıklama\",\"price\":10.00,\"stock\":5,\"categoryId\":%d}"
                                .formatted(name, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ResultActions list(String query) throws Exception {
        // URI, not String: a String is treated as a template and its %xx escapes would be encoded a second time
        return mockMvc.perform(get(URI.create("/api/v1/products?" + query)));
    }

    @Test
    void listShowsOnlyActiveProducts() throws Exception {
        Long cat = createCategory();
        Long a = createProduct("Urun " + suffix(), cat);
        Long b = createProduct("Urun " + suffix(), cat);

        mockMvc.perform(delete("/api/v1/products/{id}", b)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        list("categoryId=" + cat)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a.intValue()));
    }

    @Test
    void filtersByCategory() throws Exception {
        Long catA = createCategory();
        Long catB = createCategory();
        createProduct("Urun A1 " + suffix(), catA);
        createProduct("Urun A2 " + suffix(), catA);
        createProduct("Urun B1 " + suffix(), catB);

        list("categoryId=" + catA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].category.id", everyItem(is(catA.intValue()))));
    }

    @Test
    void newestProductComesFirstByDefault() throws Exception {
        Long cat = createCategory();
        Long first = createProduct("Urun " + suffix(), cat);
        Long second = createProduct("Urun " + suffix(), cat);

        list("categoryId=" + cat)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(second.intValue()))
                .andExpect(jsonPath("$.content[1].id").value(first.intValue()));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        String s = suffix();
        Long cat = createCategory();
        createProduct("Kırmızı Kalem " + s, cat);
        createProduct("Mavi Defter " + s, cat);

        list("categoryId=" + cat + "&q=KALEM%20" + s.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Kırmızı Kalem " + s));
    }

    @Test
    void percentSignIsNotAWildcard() throws Exception {
        Long cat = createCategory();
        createProduct("Urun " + suffix(), cat);

        // %25 is "%"; unescaped it would match every name
        list("categoryId=" + cat + "&q=%25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unsupportedSortIsRejected() throws Exception {
        list("sort=stock")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void tooLongSearchIsRejected() throws Exception {
        list("q=" + "a".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void pageLoadsCategoriesWithoutExtraQueries() throws Exception {
        Long cat = createCategory();
        createProduct("Urun " + suffix(), cat);
        createProduct("Urun " + suffix(), cat);
        createProduct("Urun " + suffix(), cat);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            PageResponse<ProductResponse> page = productService.list(new ProductFilter(cat, null),
                    PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
            assertThat(page.content()).hasSize(2);
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.content()).allSatisfy(p -> assertThat(p.category().id()).isEqualTo(cat));
            // one select for the page (category joined in) + one count query; an N+1 would add a select for the categories
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
