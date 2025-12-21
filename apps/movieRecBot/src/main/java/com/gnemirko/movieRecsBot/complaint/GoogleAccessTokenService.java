package com.gnemirko.movieRecsBot.complaint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
class GoogleAccessTokenService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com";
    private static final String TOKEN_ENDPOINT = "/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";

    private final GoogleServiceAccountSupplier accountSupplier;
    private final ObjectMapper objectMapper;
    private final WebClient tokenClient;

    private volatile TokenCache cache;

    GoogleAccessTokenService(GoogleServiceAccountSupplier accountSupplier,
                             ObjectMapper objectMapper,
                             WebClient.Builder builder) {
        this.accountSupplier = accountSupplier;
        this.objectMapper = objectMapper;
        this.tokenClient = builder.baseUrl(TOKEN_URL).build();
    }

    String accessToken() {
        TokenCache snapshot = cache;
        Instant now = Instant.now();
        if (snapshot != null && snapshot.expiresAt().isAfter(now.plusSeconds(60))) {
            return snapshot.token();
        }
        synchronized (this) {
            snapshot = cache;
            now = Instant.now();
            if (snapshot != null && snapshot.expiresAt().isAfter(now.plusSeconds(60))) {
                return snapshot.token();
            }
            String assertion = buildAssertion(now);
            TokenResponse response = requestToken(assertion);
            Instant expiry = now.plusSeconds(Math.max(60, response.expiresIn()));
            cache = new TokenCache(response.accessToken(), expiry);
            return cache.token();
        }
    }

    private String buildAssertion(Instant now) {
        GoogleServiceAccountSupplier.ServiceAccount account = accountSupplier.get();
        long issuedAt = now.getEpochSecond();
        long expiresAt = issuedAt + 3600;
        try {
            Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
            Map<String, Object> payload = Map.of(
                    "iss", account.clientEmail(),
                    "scope", SCOPE,
                    "aud", TOKEN_URL + TOKEN_ENDPOINT,
                    "iat", issuedAt,
                    "exp", expiresAt
            );

            String headerEncoded = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadEncoded = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerEncoded + "." + payloadEncoded;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(account.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureEncoded = base64Url(signature.sign());
            return signingInput + "." + signatureEncoded;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Google JWT assertion", e);
        }
    }

    private TokenResponse requestToken(String assertion) {
        return tokenClient.post()
                .uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                        .with("assertion", assertion))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Google OAuth response was empty"));
    }

    private String base64Url(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    private record TokenCache(String token, Instant expiresAt) {}

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {}
}
