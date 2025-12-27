package com.gnemirko.mcpmovie.model;

import java.util.List;

public record McpToolResponse(List<ContentBlock> content) {
    public McpToolResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
