package com.gnemirko.movieRecsBot.complaint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
class GoogleServiceAccountSupplier {

    private final ServiceAccount account;

    GoogleServiceAccountSupplier(
            @Value("${google.sheets.credentials-path:}") String credentialsPath,
            @Value("${google.sheets.credentials-json:}") String credentialsJson,
            ObjectMapper objectMapper
    ) {
        this.account = load(credentialsPath, credentialsJson, objectMapper);
    }

    ServiceAccount get() {
        return account;
    }

    private ServiceAccount load(String path, String inline, ObjectMapper objectMapper) {
        try (InputStream stream = openStream(path, inline)) {
            JsonNode root = objectMapper.readTree(stream);
            String clientEmail = getRequired(root, "client_email");
            String privateKeyPem = getRequired(root, "private_key");
            return new ServiceAccount(clientEmail, parsePrivateKey(privateKeyPem));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Google service account JSON", e);
        }
    }

    private InputStream openStream(String path, String inline) throws IOException {
        if (StringUtils.hasText(inline)) {
            return new ByteArrayInputStream(inline.getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(path)) {
            Path file = Path.of(path.trim());
            return Files.newInputStream(file);
        }
        throw new IllegalStateException("Set google.sheets.credentials-path or google.sheets.credentials-json.");
    }

    private String getRequired(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !StringUtils.hasText(node.asText())) {
            throw new IllegalStateException("Missing field '" + field + "' in service account JSON.");
        }
        return node.asText();
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            String sanitized = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(sanitized);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to parse Google service account private key", e);
        }
    }

    record ServiceAccount(String clientEmail, PrivateKey privateKey) {}
}
