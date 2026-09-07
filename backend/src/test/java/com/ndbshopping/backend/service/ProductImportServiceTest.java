package com.ndbshopping.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductImportServiceTest {

    @Test
    void parsePrice_plainDecimal() {
        assertEquals(new BigDecimal("12.99"), ProductImportService.parsePrice("12.99"));
    }

    @Test
    void parsePrice_withCurrencySymbolAndSpaces() {
        assertEquals(new BigDecimal("1234.50"), ProductImportService.parsePrice("$ 1,234.50"));
    }

    @Test
    void parsePrice_europeanDecimalComma() {
        assertEquals(new BigDecimal("1234.50"), ProductImportService.parsePrice("1.234,50 €"));
    }

    @Test
    void parsePrice_commaAsThousandsSeparator() {
        assertEquals(new BigDecimal("12345"), ProductImportService.parsePrice("12,345"));
    }

    @Test
    void parsePrice_commaAsDecimalSeparator() {
        assertEquals(new BigDecimal("12.5"), ProductImportService.parsePrice("12,5"));
    }

    @Test
    void parsePrice_blankOrUnparsable() {
        assertNull(ProductImportService.parsePrice(null));
        assertNull(ProductImportService.parsePrice(""));
        assertNull(ProductImportService.parsePrice("N/A"));
    }
}
