package com.ndbshopping.backend.entity.enums;

import java.util.Locale;

public enum ProductSource {
    MANUEL,
    FACEBOOK,
    ALIBABA,
    ALIEXPRESS,
    AMAZON,
    AUTRE;

    /** Détecte la plateforme d'origine à partir du nom d'hôte d'une URL produit externe. */
    public static ProductSource fromUrl(String url) {
        if (url == null) {
            return AUTRE;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("facebook.com") || lower.contains("fb.com") || lower.contains("fb.watch")) {
            return FACEBOOK;
        }
        if (lower.contains("aliexpress.com")) {
            return ALIEXPRESS;
        }
        if (lower.contains("alibaba.com") || lower.contains("1688.com")) {
            return ALIBABA;
        }
        if (lower.contains("amazon.")) {
            return AMAZON;
        }
        return AUTRE;
    }
}
