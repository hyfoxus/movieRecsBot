package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatusCommands {

    private final RecommendationTaskRepository repo;

    public String statusForChat(Long chatId, String argOrNull) {
        if (argOrNull == null || argOrNull.isBlank()) {
            List<RecommendationTask> last = repo.findTop20ByChatIdOrderByCreatedAtDesc(chatId);
            if (last.isEmpty()) return "Задач нет.";
            StringBuilder sb = new StringBuilder("Последние задачи:\n");
            last.forEach(t -> sb.append(line(t)).append("\n"));
            return sb.toString();
        } else {
            try {
                long id = Long.parseLong(argOrNull.trim());
                var t = repo.findById(id).orElse(null);
                return t == null ? "Нет такой задачи." : line(t) +
                        (t.getError() == null ? "" : "\nОшибка: " + t.getError());
            } catch (NumberFormatException e) {
                return "Неверный формат: /status <id>";
            }
        }
    }

    private static String line(RecommendationTask t) {
        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        return "• #" + t.getId() + " — " + t.getStatus() +
                " (" + fmt.format(t.getCreatedAt()) + ")";
    }
}