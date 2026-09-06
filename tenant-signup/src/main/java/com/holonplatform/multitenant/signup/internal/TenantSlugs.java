package com.holonplatform.multitenant.signup.internal;

import java.util.Locale;
import java.util.UUID;

/** Internal slug-generation helper shared by {@code SignupService}. */
public final class TenantSlugs {

    private TenantSlugs() {
    }

    public static String slugify(String companyName) {
        String source = companyName != null ? companyName : "";
        String slug = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() < 2) {
            slug = (slug + "-ws" + Math.abs(UUID.randomUUID().hashCode() % 1000));
        }
        return slug;
    }
}

