package com.gnemirko.movieRecsBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("recExecutor")
    public Executor recExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        int cores = Math.max(4, Runtime.getRuntime().availableProcessors());
        ex.setCorePoolSize(cores);
        ex.setMaxPoolSize(cores * 2);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("rec-");
        ex.setAwaitTerminationSeconds(30);
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.initialize();
        return ex;
    }
}