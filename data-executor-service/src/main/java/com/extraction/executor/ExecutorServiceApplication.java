package com.extraction.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ExecutorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorServiceApplication.class, args);
    }
}
