package com.gnemirko.movieRecsBot.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;

@Configuration
public class SecurityConfig {

    private final AntPathMatcher antMatcher = new AntPathMatcher();
    private final TelegramWebhookProperties webhookProperties;
    private final String webhookSecret;

    public SecurityConfig(TelegramWebhookProperties webhookProperties,
                          @Value("${telegram.bot.webhook-secret:}") String webhookSecret) {
        this.webhookProperties = webhookProperties;
        this.webhookSecret = webhookSecret;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        String normalizedPath = webhookProperties.getNormalizedPath();
        RequestMatcher webhookMatcher = request ->
                antMatcher.match(normalizedPath, requestPath(request));
        RequestMatcher webhookChildrenMatcher = request ->
                antMatcher.match(normalizedPath + "/**", requestPath(request));

        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(webhookMatcher, webhookChildrenMatcher))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(normalizedPath, normalizedPath + "/**")
                        .permitAll()
                        .requestMatchers(PathRequest.toH2Console())
                        .permitAll()
                        .anyRequest().permitAll()
                )
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(new WebhookSecretFilter(webhookSecret, webhookProperties), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * {@code request.getServletPath()} returns an empty string under MockMvc's test dispatcher
     * (confirmed empirically), even though it holds the full path in a real deployed servlet
     * container with the default "/" mapping — so path matching here uses the request URI (minus
     * context path) instead, which behaves consistently in both environments.
     */
    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri != null && contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri == null ? "" : uri;
    }
}
