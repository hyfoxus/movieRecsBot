package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import com.gnemirko.movieRecsBot.service.UserProfileSnapshot;
import com.gnemirko.movieRecsBot.util.CmdText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProfileCommandHandler {

    private final UserProfileService userProfileService;

    public String profileText(long chatId) {
        UserProfileSnapshot profile = userProfileService.snapshot(chatId);
        String genres = profile.likedGenres().isEmpty() ? "—" : String.join(", ", profile.likedGenres());
        String actors = profile.likedActors().isEmpty() ? "—" : String.join(", ", profile.likedActors());
        String directors = profile.likedDirectors().isEmpty() ? "—" : String.join(", ", profile.likedDirectors());
        String blocked = profile.blocked().isEmpty() ? "—" : String.join(", ", profile.blocked());
        String watched = profile.watchedMovies().isEmpty()
                ? " —"
                : "\n  " + profile.watchedMovies().stream()
                .limit(3)
                .map(this::formatOpinion)
                .collect(Collectors.joining("\n  "));

        return """
                *Профиль*:
                • Жанры: %s
                • Актёры: %s
                • Режиссёры: %s
                • Не предлагать: %s
                • Просмотры:%s
                """.formatted(
                CmdText.esc(genres),
                CmdText.esc(actors),
                CmdText.esc(directors),
                CmdText.esc(blocked),
                watched).trim();
    }

    public String helpText() {
        return """
                *Как обновлять профиль*:
                • Нажми /menu чтобы открыть интерактивное меню и изменить жанры, актёров, режиссёров или анти-метки
                • Выбирай действие в меню — бот задаст уточняющий вопрос и запомнит ответ
                • Команда /profile показывает текущие настройки, /status — состояние очереди рекомендаций
                """;
    }

    private String formatOpinion(MovieOpinion opinion) {
        if (opinion == null) {
            return "—";
        }
        String title = CmdText.esc(nvl(opinion.getTitle()));
        String review = CmdText.esc(nvl(opinion.getOpinion()));
        if (review.isBlank()) {
            return title;
        }
        return title + " — " + review;
    }

    private static String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}
