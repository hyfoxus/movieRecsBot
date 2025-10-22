package com.gnemirko.imdbvec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ImdbVecApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImdbVecApplication.class, args);
    }
}
