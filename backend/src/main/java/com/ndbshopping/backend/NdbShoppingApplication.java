package com.ndbshopping.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NdbShoppingApplication {

    public static void main(String[] args) {
        SpringApplication.run(NdbShoppingApplication.class, args);
    }
}
