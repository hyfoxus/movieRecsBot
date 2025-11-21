package com.gnemirko.normalizer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NormalizationRequest {

    @NotBlank(message = "text is required")
    private String text;

    @NotBlank(message = "targetLanguage is required")
    @Pattern(regexp = "[a-zA-Z]{2}", message = "targetLanguage must be ISO-639-1 code")
    private String targetLanguage;
}
