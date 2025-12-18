package com.gnemirko.movieRecsBot.complaint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
class GoogleSheetsClient {

    private final GoogleAccessTokenService accessTokenService;
    private final WebClient sheetsWebClient;

    GoogleSheetsClient(GoogleAccessTokenService accessTokenService, WebClient.Builder builder) {
        this.accessTokenService = accessTokenService;
        this.sheetsWebClient = builder.baseUrl("https://sheets.googleapis.com").build();
    }

    void appendRow(String spreadsheetId, String range, List<String> values) {
        try {
            sheetsWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v4/spreadsheets/{spreadsheetId}/values/{range}:append")
                            .queryParam("valueInputOption", "RAW")
                            .queryParam("insertDataOption", "INSERT_ROWS")
                            .build(spreadsheetId, range))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessTokenService.accessToken()))
                    .body(BodyInserters.fromValue(Map.of("values", List.of(values))))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Google Sheets rejected the complaint append request: status={}, body={}",
                    e.getRawStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to call Google Sheets API", e);
        }
    }
}
