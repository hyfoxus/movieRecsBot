package com.gnemirko.mcpmovie.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ResourceQuery(@NotEmpty(message = "resources are required")
                            List<@Valid ResourcePointer> resources) {
    public ResourceQuery {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
