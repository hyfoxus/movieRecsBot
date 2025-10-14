package com.gnemirko.movieRecsBot.dto;

import java.util.List;

public record RecResponse(String intro, List<Movie> movies) {
    public record Movie(String title, Integer year, String reason, List<String> genres) {}
}
