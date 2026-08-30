package com.ndbshopping.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChinguisoftSmsResponse(Integer code, Integer balance) {
}
