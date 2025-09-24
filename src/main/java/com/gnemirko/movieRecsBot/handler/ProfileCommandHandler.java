package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.handler.ProfileCommands;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import com.gnemirko.movieRecsBot.util.CmdText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ProfileCommandHandler {

    private final UserProfileService userProfileService;
    private final java.util.Map<Long, Target> pending = new java.util.concurrent.ConcurrentHashMap<>();

    public String handle(long chatId, String text) {
        ProfileCommands cmd = ProfileCommands.match(text);
        if (cmd == null) {
            Target wait = pending.remove(chatId);
            if (wait != null) {
                return addWithTarget(chatId, text, wait); // обработать как значения
            }
            return null;
        }

        switch (cmd) {
            case PROFILE -> { return show(chatId); }
            case HELP -> { return help(); }
            case RESET -> { userProfileService.reset(chatId); return "Профиль очищен\\."; }
            case LIKE_GENRE -> { return add(chatId, List.of(text), Target.GENRE); }
            case LIKE_ACTOR -> { return add(chatId, List.of(text), Target.ACTOR); }
            case LIKE_DIRECTOR -> { return add(chatId, List.of(text), Target.DIRECTOR); }
            case BLOCK -> { return add(chatId, List.of(text), Target.ANTI); }
            case UNBLOCK -> { return remove(chatId, text); }
        }
        return null;
    }

    private String show(long chatId) {
        UserProfile p = userProfileService.getOrCreate(chatId);
        String g = p.getLikedGenres().isEmpty() ? "—" : String.join(", ", p.getLikedGenres());
        String a = p.getLikedActors().isEmpty() ? "—" : String.join(", ", p.getLikedActors());
        String d = p.getLikedDirectors().isEmpty() ? "—" : String.join(", ", p.getLikedDirectors());
        String b = p.getBlocked().isEmpty() ? "—" : String.join(", ", p.getBlocked());

        return """
                *Профиль*:
                • Жанры: %s
                • Актёры: %s
                • Режиссёры: %s
                • Не предлагать: %s
                """.formatted(CmdText.esc(g), CmdText.esc(a), CmdText.esc(d), CmdText.esc(b)).trim();
    }


    private String help() {
        return """
                *Команды*:
                /profile — показать профиль
                /like_genre фэнтези, комедия
                /like_actor Том Хиддлстон
                /like_director Кристофер Нолан
                /block хоррор, детское
                /unblock хоррор
                /reset_profile — очистить профиль
                """;
    }

    private String addWithTarget(long chatId, String text, Target t) {
        var vals = CmdText.parseArgs("x " + text); // трюк: парсим как будто после команды
        if (vals.isEmpty()) vals = java.util.List.of(text.split("\\s*,\\s*"));
        return add(chatId, vals, t);
    }

    private String add(long chatId, java.util.List<String> vals, Target t) {
        var normed = vals.stream().map(this::norm).filter(s -> !s.isBlank()).toList();
        if (normed.isEmpty()) return "Нужно хотя бы одно значение\\.";
        switch (t) {
            case GENRE -> userProfileService.addGenres(chatId, normed);
            case ACTOR -> userProfileService.addActors(chatId, normed);
            case DIRECTOR -> userProfileService.addDirectors(chatId, normed);
            case ANTI -> userProfileService.blockTags(chatId, normed);
        }
        return "Готово: " + CmdText.esc(String.join(", ", normed));
    }

    private String remove(long chatId, String text) {
        List<String> vals = CmdText.parseArgs(text).stream()
                .map(this::norm).toList();
        if (vals.isEmpty()) return "Укажи, что убрать\\.";
        for (String v : vals) userProfileService.unblockTag(chatId, v);
        return "Удалено: " + CmdText.esc(String.join(", ", vals));
    }

    private String norm(String s) {
        return s.trim().replaceAll("\\s+", " ").replace("ё","е").toLowerCase(Locale.ROOT);
    }

    private enum Target { GENRE, ACTOR, DIRECTOR, ANTI }
}