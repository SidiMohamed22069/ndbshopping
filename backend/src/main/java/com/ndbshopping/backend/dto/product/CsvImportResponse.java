package com.ndbshopping.backend.dto.product;

import java.util.List;

public record CsvImportResponse(int imported, List<CsvImportError> errors) {

    public record CsvImportError(int line, String message) {
    }
}
