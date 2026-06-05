package com.courtflow.homework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CourtflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourtflowApplication.class, args);
    }

}
