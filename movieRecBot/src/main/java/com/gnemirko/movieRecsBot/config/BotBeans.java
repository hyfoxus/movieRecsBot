package com.gnemirko.movieRecsBot.config;

import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotBeans {
    @Bean DialogPolicy dialogPolicy() { return new DialogPolicy(); }
}