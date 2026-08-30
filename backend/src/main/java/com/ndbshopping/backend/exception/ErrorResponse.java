package com.ndbshopping.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record ErrorResponse(String error, int status) {

    public static ResponseEntity<ErrorResponse> of(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, status.value()));
    }
}
