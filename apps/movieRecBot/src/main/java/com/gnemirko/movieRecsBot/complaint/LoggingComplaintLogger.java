package com.gnemirko.movieRecsBot.complaint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingComplaintLogger implements ComplaintLogger {
    @Override
    public void log(ComplaintRecord record) {
        log.info("Complaint captured: chatId={}, user={}, to='{}', text='{}'",
                record.chatId(),
                record.userLabel(),
                record.targetDescription(),
                record.complaintText());
    }
}
