package com.gnemirko.movieRecsBot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;

@Configuration
public class SecurityConfig {

    private final AntPathMatcher antMatcher = new AntPathMatcher();

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            @Value("${telegram.bot.webhook-path:/tg/webhook}") String webhookPath
    ) throws Exception {

        String normalizedPath = normalizePath(webhookPath);
        RequestMatcher webhookMatcher = request ->
                antMatcher.match(normalizedPath, request.getServletPath());
        RequestMatcher webhookChildrenMatcher = request ->
                antMatcher.match(normalizedPath + "/**", request.getServletPath());

        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(webhookMatcher, webhookChildrenMatcher))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(normalizedPath, normalizedPath + "/**")
                        .permitAll()
                        .requestMatchers(PathRequest.toH2Console())
                        .permitAll()
                        .anyRequest().permitAll()
                )
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "/tg/webhook";
        String trimmed = rawPath.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
