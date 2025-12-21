package com.gnemirko.movieRecsBot.complaint;

import java.time.Instant;

public record ComplaintRecord(
        long chatId,
        Long userId,
        String userLabel,
        String complaintText,
        String targetDescription,
        Instant createdAt
) {
    public static ComplaintRecord of(long chatId,
                                     Long userId,
                                     String userLabel,
                                     String complaintText,
                                     String targetDescription) {
        return new ComplaintRecord(
                chatId,
                userId,
                userLabel,
                complaintText,
                targetDescription,
                Instant.now()
        );
    }
}
