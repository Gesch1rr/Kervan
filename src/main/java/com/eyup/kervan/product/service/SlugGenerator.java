package com.eyup.kervan.product.service;

import com.eyup.kervan.product.exception.InvalidNameException;

import java.text.Normalizer;
import java.util.Locale;

final class SlugGenerator {

    private SlugGenerator() {
    }

    static String toSlug(String name) {
        // dotless ı has no decomposition, map it by hand
        String slug = name.toLowerCase(Locale.ROOT).replace('ı', 'i');
        // ç->c, ğ->g, ö->o, ş->s, ü->u, i̇->i
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        slug = slug.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            throw new InvalidNameException();
        }
        return slug;
    }
}
