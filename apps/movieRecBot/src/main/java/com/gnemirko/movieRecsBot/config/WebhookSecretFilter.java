package com.gnemirko.movieRecsBot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Rejects POSTs to the Telegram webhook path unless they carry the secret token Telegram is
 * configured (via {@code setWebhook}'s {@code secret_token}, see {@link TelegramConfig}) to echo
 * back on every update. Without this, anyone who discovers the webhook URL could POST arbitrary
 * {@code Update} payloads and impersonate any chat/user.
 *
 * <p>Deliberately NOT a Spring bean: it's constructed directly by {@link SecurityConfig} and wired
 * into the security filter chain via {@code addFilterBefore}. Registering it as a plain
 * {@code @Component} would make Spring Boot's test/MockMvc auto-configuration additionally treat it
 * as a standalone servlet filter applied outside Spring Security's chain, double-registering it.
 */
@Slf4j
public class WebhookSecretFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final String expectedSecret;
    private final TelegramWebhookProperties webhookProperties;

    public WebhookSecretFilter(String expectedSecret, TelegramWebhookProperties webhookProperties) {
        this.expectedSecret = expectedSecret;
        this.webhookProperties = webhookProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isWebhookPost(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(SECRET_HEADER);
        if (!matchesExpectedSecret(provided)) {
            log.warn("Rejected webhook POST from {} — missing or invalid {} header", request.getRemoteAddr(), SECRET_HEADER);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWebhookPost(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = webhookProperties.getNormalizedPath();
        String requestPath = requestPath(request);
        return path.equals(requestPath) || (path + "/").equals(requestPath);
    }

    /**
     * {@code request.getServletPath()} returns an empty string under MockMvc's test dispatcher even
     * though it holds the full path in a real deployed servlet container with the default "/"
     * mapping — see the matching note in {@link SecurityConfig}. Using the request URI here keeps
     * this filter's path check consistent with {@code SecurityConfig}'s CSRF-exemption matcher.
     */
    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri != null && contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri == null ? "" : uri;
    }

    private boolean matchesExpectedSecret(String provided) {
        if (provided == null || expectedSecret == null || expectedSecret.isBlank()) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }
}
