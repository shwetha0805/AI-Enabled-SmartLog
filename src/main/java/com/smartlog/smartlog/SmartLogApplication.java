package com.smartlog.smartlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartLogApplication {

    public static void main(String[] args) {

        SpringApplication.run(
                SmartLogApplication.class,
                args
        );
    }
}