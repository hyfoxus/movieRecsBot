package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.mcp.MovieContextItem;
import com.gnemirko.movieRecsBot.service.UserLanguage;

import java.util.List;

public record PromptContext(UserProfile profile,
                            UserLanguage language,
                            String profileSummary,
                            String history,
                            String movieContext,
                            List<MovieContextItem> catalogItems,
                            UserIntent userIntent) {
}
