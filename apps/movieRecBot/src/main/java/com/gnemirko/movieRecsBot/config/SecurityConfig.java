package com.gnemirko.movieRecsBot.config;

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
    private final TelegramWebhookProperties webhookProperties;

    public SecurityConfig(TelegramWebhookProperties webhookProperties) {
        this.webhookProperties = webhookProperties;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        String normalizedPath = webhookProperties.getNormalizedPath();
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

}
