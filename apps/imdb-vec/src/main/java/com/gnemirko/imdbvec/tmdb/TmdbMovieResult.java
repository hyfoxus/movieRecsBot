package com.gnemirko.imdbvec.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieResult(
        @JsonProperty("id") long id,
        @JsonProperty("overview") String overview
) {
}
