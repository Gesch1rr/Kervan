package com.eyup.kervan.product.service;

import com.eyup.kervan.product.exception.InvalidNameException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugGeneratorTest {

    @Test
    void turkishLettersAreTransliterated() {
        assertThat(SlugGenerator.toSlug("Ev & Yaşam")).isEqualTo("ev-yasam");
        assertThat(SlugGenerator.toSlug("Çocuk Giyim")).isEqualTo("cocuk-giyim");
        assertThat(SlugGenerator.toSlug("Işık Ürünleri")).isEqualTo("isik-urunleri");
        assertThat(SlugGenerator.toSlug("İçecekler")).isEqualTo("icecekler");
        assertThat(SlugGenerator.toSlug("ĞÖŞÜÇ")).isEqualTo("gosuc");
    }

    @Test
    void separatorsCollapseAndEdgesAreTrimmed() {
        assertThat(SlugGenerator.toSlug("  Bilgisayar  ve -- Tablet! ")).isEqualTo("bilgisayar-ve-tablet");
        assertThat(SlugGenerator.toSlug("4K TV")).isEqualTo("4k-tv");
    }

    @Test
    void nameWithoutLatinCharactersIsRejected() {
        assertThatThrownBy(() -> SlugGenerator.toSlug("!!!"))
                .isInstanceOf(InvalidNameException.class);
    }

    @Test
    void resultDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(SlugGenerator.toSlug("IŞIK")).isEqualTo("isik");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
