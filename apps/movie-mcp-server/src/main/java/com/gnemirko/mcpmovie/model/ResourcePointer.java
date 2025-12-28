package com.gnemirko.mcpmovie.model;

import jakarta.validation.constraints.NotBlank;

public record ResourcePointer(@NotBlank(message = "uri is required") String uri) {
}
