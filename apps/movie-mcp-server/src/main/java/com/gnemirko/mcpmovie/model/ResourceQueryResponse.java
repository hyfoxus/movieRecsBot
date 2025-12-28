package com.gnemirko.mcpmovie.model;

import java.util.List;

public record ResourceQueryResponse(List<ResourceResult> results) {
    public ResourceQueryResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
