package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserMessage;
import com.gnemirko.movieRecsBot.repository.UserMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContextServiceTest {

    @Mock
    private UserMessageRepository repo;
    @Mock
    private HistorySummarizer historySummarizer;

    private UserContextService service;

    @BeforeEach
    void setUp() {
        service = new UserContextService(repo, historySummarizer);
    }

    @Test
    void historyAsOneStringAlwaysIncludesSummariesAheadOfRawMessages() {
        long chatId = 1L;
        Instant base = Instant.now();
        UserMessage summary = message(chatId, "Summary: likes noir, dislikes horror", base.minus(10, ChronoUnit.DAYS));
        UserMessage raw1 = message(chatId, "User: hi", base.minusSeconds(20));
        UserMessage raw2 = message(chatId, "Bot: hello", base.minusSeconds(10));

        when(repo.findSummaries(chatId)).thenReturn(List.of(summary));
        when(repo.findRawDesc(chatId, PageRequest.of(0, 30)))
                .thenReturn(List.of(raw2, raw1));

        String history = service.historyAsOneString(chatId, 30, 300);

        assertThat(history.split("\n")).containsExactly(
                "Summary: likes noir, dislikes horror", "User: hi", "Bot: hello");
    }

    @Test
    void compactIfNeededDoesNothingBelowTrigger() {
        when(repo.countRawByChatId(1L)).thenReturn(10L);

        service.compactIfNeeded(1L);

        verify(repo, never()).findRawAscForCompaction(anyLong());
    }

    @Test
    void compactIfNeededSummarizesOverflowAndKeepsRawWindow() {
        long chatId = 2L;
        when(repo.countRawByChatId(chatId)).thenReturn(50L);
        List<UserMessage> all = new ArrayList<>();
        Instant base = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 50; i++) {
            all.add(message(chatId, "Turn " + i, base.plusSeconds(i)));
        }
        when(repo.findRawAscForCompaction(chatId)).thenReturn(all);
        when(historySummarizer.summarize(any())).thenReturn("compressed preferences");

        service.compactIfNeeded(chatId);

        ArgumentCaptor<List<String>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(historySummarizer).summarize(linesCaptor.capture());
        assertThat(linesCaptor.getValue()).hasSize(20);

        ArgumentCaptor<List<UserMessage>> deletedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repo).deleteAllInBatch(deletedCaptor.capture());
        assertThat(deletedCaptor.getValue()).hasSize(20);

        verify(repo).save(argThatSummaryRow());
    }

    @Test
    void compactIfNeededSkipsSaveWhenSummarizerFails() {
        long chatId = 3L;
        when(repo.countRawByChatId(chatId)).thenReturn(50L);
        List<UserMessage> all = new ArrayList<>();
        Instant base = Instant.now();
        for (int i = 0; i < 50; i++) {
            all.add(message(chatId, "Turn " + i, base.plusSeconds(i)));
        }
        when(repo.findRawAscForCompaction(chatId)).thenReturn(all);
        when(historySummarizer.summarize(any())).thenReturn(null);

        service.compactIfNeeded(chatId);

        verify(repo, never()).deleteAllInBatch(any());
        verify(repo, never()).save(any());
    }

    private static UserMessage argThatSummaryRow() {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getText().startsWith("Summary: compressed preferences"));
    }

    private static UserMessage message(long chatId, String text, Instant createdAt) {
        return UserMessage.builder().chatId(chatId).text(text).createdAt(createdAt).build();
    }
}
