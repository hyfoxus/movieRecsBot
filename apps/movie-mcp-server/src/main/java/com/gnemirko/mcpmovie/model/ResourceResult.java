package com.gnemirko.mcpmovie.model;

import java.util.List;

public record ResourceResult(String uri, List<ContentBlock> content) {
    public ResourceResult {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
