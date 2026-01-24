package com.gnemirko.movieRecsBot.service.recommendation;

import java.util.List;

public record UserIntent(List<String> actorNames,
                         List<String> includeGenres,
                         List<String> excludeGenres,
                         List<String> descriptors,
                         Integer runtimeMinutes,
                         String rewrittenQuery,
                         String summary) {

    public static UserIntent empty() {
        return new UserIntent(List.of(), List.of(), List.of(), List.of(), null, "", "");
    }

    public boolean hasActors() {
        return actorNames != null && !actorNames.isEmpty();
    }
}
