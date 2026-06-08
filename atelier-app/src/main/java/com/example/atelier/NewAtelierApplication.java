package com.example.atelier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.atelier")
public class NewAtelierApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewAtelierApplication.class, args);
    }
}
