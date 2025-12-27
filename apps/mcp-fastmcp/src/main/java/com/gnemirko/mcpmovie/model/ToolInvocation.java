package com.gnemirko.mcpmovie.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ToolInvocation(
        @NotBlank(message = "name is required") String name,
        Map<String, Object> arguments
) {
    public ToolInvocation {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
