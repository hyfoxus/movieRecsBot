package com.gnemirko.movieRecsBot.service.recommendation;

import java.util.List;

public record UserIntent(List<String> actorNames,
                         List<String> includeGenres,
                         List<String> excludeGenres,
                         List<String> descriptors,
                         Integer runtimeMinutes,
                         String rewrittenQuery,
                         String summary,
                         IntentType intentType,
                         String requestedTitle) {

    public static UserIntent empty() {
        return new UserIntent(List.of(), List.of(), List.of(), List.of(), null, "", "", IntentType.RECOMMENDATION, "");
    }

    public boolean hasActors() {
        return actorNames != null && !actorNames.isEmpty();
    }

    public boolean isInformationRequest() {
        return intentType == IntentType.INFORMATION && requestedTitle != null && !requestedTitle.isBlank();
    }

    public boolean isRecommendationRequest() {
        return intentType == null || intentType == IntentType.RECOMMENDATION;
    }
}
