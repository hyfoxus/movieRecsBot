// src/main/java/.../config/SecurityConfig.java
package com.gnemirko.movieRecsBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/tg/webhook"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/tg/webhook", "/h2-console/**").permitAll()
                        .anyRequest().permitAll()
                )
                .headers(h -> h.frameOptions(f -> f.sameOrigin())); // для H2-консоли
        return http.build();
    }
}