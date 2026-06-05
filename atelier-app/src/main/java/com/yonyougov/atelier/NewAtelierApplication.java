package com.yonyougov.atelier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.yonyougov.atelier")
public class NewAtelierApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewAtelierApplication.class, args);
    }
}
