package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import com.gnemirko.movieRecsBot.mcp.MovieContextService.ContextBlock;
import com.gnemirko.movieRecsBot.service.ActorMentionExtractor;
import com.gnemirko.movieRecsBot.service.UserContextService;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptContextBuilder {

    private final UserProfileService userProfileService;
    private final UserContextService userContextService;
    private final MovieContextService movieContextService;

    public PromptContext build(long chatId, String normalizedUserText, UserLanguage language) {
        UserProfile profile = userProfileService.getOrCreate(chatId);
        String profileSummary = buildProfileSummary(profile);
        String history = userContextService.historyAsOneString(chatId, 30, 300);
        List<String> actorFilters = resolveActorFilters(normalizedUserText, profile);
        ContextBlock block = movieContextService.buildContextBlock(
                normalizedUserText,
                profileSummary,
                profile,
                language,
                actorFilters);
        return new PromptContext(
                profile,
                language,
                profileSummary,
                history,
                block.block(),
                block.items()
        );
    }

    private List<String> resolveActorFilters(String userText, UserProfile profile) {
        LinkedHashSet<String> filters = new LinkedHashSet<>();
        filters.addAll(ActorMentionExtractor.extract(userText));
        if (profile != null && profile.getLikedActors() != null) {
            filters.addAll(profile.getLikedActors());
        }
        return filters.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(5)
                .toList();
    }

    private String buildProfileSummary(UserProfile profile) {
        if (profile == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!profile.getLikedGenres().isEmpty()) sb.append("Preferred genres: ").append(profile.getLikedGenres()).append(". ");
        if (!profile.getLikedActors().isEmpty()) sb.append("Favorite actors: ").append(profile.getLikedActors()).append(". ");
        if (!profile.getLikedDirectors().isEmpty())
            sb.append("Favorite directors: ").append(profile.getLikedDirectors()).append(". ");
        if (!profile.getBlocked().isEmpty()) sb.append("Block list: ").append(profile.getBlocked()).append(". ");
        if (!profile.getWatchedMovies().isEmpty()) {
            String watched = profile.getWatchedMovies().stream()
                    .limit(5)
                    .map(this::shortOpinion)
                    .collect(Collectors.joining("; "));
            if (!watched.isEmpty()) sb.append("Recent opinions: ").append(watched).append(". ");
        }
        return sb.toString().trim();
    }

    private String shortOpinion(MovieOpinion opinion) {
        if (opinion == null) return "";
        String title = nvl(opinion.getTitle()).trim();
        String review = nvl(opinion.getOpinion()).trim();
        if (title.isEmpty() && review.isEmpty()) return "";
        if (review.isEmpty()) return title;
        return title + " — " + review;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
