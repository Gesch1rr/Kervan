package com.eyup.kervan.product.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSpecificationsTest {

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(ProductSpecifications.escapeLike("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    void backslashIsEscapedToo() {
        assertThat(ProductSpecifications.escapeLike("a\\b")).isEqualTo("a\\\\b");
    }
}
