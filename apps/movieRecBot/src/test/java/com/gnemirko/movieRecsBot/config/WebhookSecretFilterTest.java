package com.gnemirko.movieRecsBot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookSecretFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private WebhookSecretFilter filter(String configuredSecret) {
        TelegramWebhookProperties properties = new TelegramWebhookProperties("/tg/webhook");
        return new WebhookSecretFilter(configuredSecret, properties);
    }

    @BeforeEach
    void setUpWebhookRequest() {
        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getRequestURI()).thenReturn("/tg/webhook");
        lenient().when(request.getContextPath()).thenReturn("");
    }

    @Test
    void allowsRequestWithMatchingSecretHeader() throws Exception {
        when(request.getHeader(WebhookSecretFilter.SECRET_HEADER)).thenReturn("correct-secret");

        filter("correct-secret").doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void rejectsRequestMissingSecretHeader() throws Exception {
        when(request.getHeader(WebhookSecretFilter.SECRET_HEADER)).thenReturn(null);

        filter("correct-secret").doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsRequestWithWrongSecretHeader() throws Exception {
        when(request.getHeader(WebhookSecretFilter.SECRET_HEADER)).thenReturn("wrong-secret");

        filter("correct-secret").doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() throws Exception {
        when(request.getHeader(WebhookSecretFilter.SECRET_HEADER)).thenReturn("anything");

        filter("").doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void ignoresNonWebhookRequestsRegardlessOfHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter("correct-secret").doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void ignoresGetRequestsToTheWebhookPath() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        filter("correct-secret").doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
    }
}
