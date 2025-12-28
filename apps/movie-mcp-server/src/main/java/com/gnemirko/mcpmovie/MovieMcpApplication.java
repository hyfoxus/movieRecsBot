package com.gnemirko.mcpmovie;

import com.gnemirko.mcpmovie.config.MovieMcpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(MovieMcpProperties.class)
@SpringBootApplication
public class MovieMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieMcpApplication.class, args);
    }
}
