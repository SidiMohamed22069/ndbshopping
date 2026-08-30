package com.ndbshopping.backend.exception;

import org.springframework.http.HttpStatus;

public class SmsBalanceDepletedException extends ApiException {

    public SmsBalanceDepletedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Solde SMS épuisé. Réessayez plus tard.");
    }
}
