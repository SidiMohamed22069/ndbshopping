package com.ndbshopping.backend.service;

import com.ndbshopping.backend.entity.enums.ProductSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductServiceTest {

    @Test
    void detectSource_facebook() {
        assertEquals(ProductSource.FACEBOOK, ProductService.detectSource("https://www.facebook.com/marketplace/item/1"));
    }

    @Test
    void detectSource_alibaba() {
        assertEquals(ProductSource.ALIBABA, ProductService.detectSource("https://www.alibaba.com/product-detail/foo.html"));
    }

    @Test
    void detectSource_other() {
        assertEquals(ProductSource.AUTRE, ProductService.detectSource("https://example.com/item/1"));
    }

    @Test
    void detectVideoExtension_mp4AndWebm() {
        assertEquals(".mp4", FileStorageService.detectVideoExtension(
                new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}));
        assertEquals(".webm", FileStorageService.detectVideoExtension(
                new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 1, 2, 3}));
        assertNull(FileStorageService.detectVideoExtension(
                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4, 5, 6}));
    }
}
