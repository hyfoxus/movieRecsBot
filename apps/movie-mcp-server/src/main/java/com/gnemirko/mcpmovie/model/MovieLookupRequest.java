package com.gnemirko.mcpmovie.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MovieLookupRequest(
        @NotBlank(message = "title is required")
        String title,
        @Min(value = 1888, message = "year must be >= 1888")
        Integer year
) {
}
