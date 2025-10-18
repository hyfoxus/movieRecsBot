package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import com.gnemirko.movieRecsBot.util.CmdText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.gnemirko.movieRecsBot.entity.RecommendationTask.Status.QUEUED;
import static com.gnemirko.movieRecsBot.entity.RecommendationTask.Status.RUNNING;

@Component
@RequiredArgsConstructor
public class StatusCommands {

    private static final List<RecommendationTask.Status> ACTIVE_STATUSES = List.of(QUEUED, RUNNING);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final RecommendationTaskRepository repo;

    public String statusForChat(Long chatId, String argOrNull) {
        if (argOrNull == null || argOrNull.isBlank()) {
            List<RecommendationTask> pending = repo.findByChatIdAndStatusInOrderByCreatedAtAsc(chatId, ACTIVE_STATUSES);
            if (pending.isEmpty()) {
                return "Очередь пуста.";
            }
            StringBuilder sb = new StringBuilder("Активные задачи:\n");
            for (int i = 0; i < pending.size(); i++) {
                sb.append(line(pending.get(i), i + 1)).append("\n");
            }
            return sb.toString().trim();
        }

        String displayId = argOrNull.trim().toUpperCase(Locale.ROOT);
        return repo.findByDisplayId(displayId)
                .filter(task -> chatId.equals(task.getChatId()))
                .map(task -> line(task, -1))
                .orElse("Нет такой задачи.");
    }

    private String line(RecommendationTask task, int position) {
        String prefix = position > 0 ? position + ") " : "";
        String id = task.getDisplayId() != null ? task.getDisplayId() : String.valueOf(task.getId());
        String raw = prefix + "#" + id + " — " + task.getStatus() +
                " (" + TIME_FMT.format(task.getCreatedAt()) + ")";
        return CmdText.esc(raw);
    }
}
