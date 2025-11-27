package com.gnemirko.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateResponse(String response, boolean done) {
}
