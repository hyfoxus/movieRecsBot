package com.gnemirko.imdbvec.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbFindResponse(
        @JsonProperty("movie_results") List<TmdbMovieResult> movieResults
) {
    public TmdbFindResponse {
        movieResults = movieResults == null ? Collections.emptyList() : List.copyOf(movieResults);
    }
}
