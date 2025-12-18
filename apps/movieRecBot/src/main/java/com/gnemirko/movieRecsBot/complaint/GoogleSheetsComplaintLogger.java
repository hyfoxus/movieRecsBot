package com.gnemirko.movieRecsBot.complaint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsComplaintLogger implements ComplaintLogger {

    private final GoogleSheetsClient sheetsClient;
    private final String spreadsheetId;
    private final String range;

    public GoogleSheetsComplaintLogger(
            GoogleSheetsClient sheetsClient,
            @Value("${google.sheets.spreadsheet-id}") String spreadsheetId,
            @Value("${google.sheets.range:Complaints!A:C}") String range
    ) {
        this.sheetsClient = sheetsClient;
        this.spreadsheetId = spreadsheetId;
        this.range = range;
    }

    @Override
    public void log(ComplaintRecord record) {
        if (!StringUtils.hasText(spreadsheetId)) {
            throw new IllegalStateException("google.sheets.spreadsheet-id must be set when Google Sheets logging is enabled.");
        }
        try {
            sheetsClient.appendRow(
                    spreadsheetId.trim(),
                    range == null ? "Complaints!A:C" : range,
                    List.of(
                            safe(record.userLabel()),
                            safe(record.complaintText()),
                            safe(record.targetDescription())
                    ));
        } catch (Exception e) {
            log.error("Failed to write complaint to Google Sheets: {}", e.getMessage(), e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
