package com.gnemirko.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerateRequest(
        String model,
        String prompt,
        boolean stream,
        Options options
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Options(Double temperature) {
    }
}
