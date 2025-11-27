package com.gnemirko.normalizer.controller;

import com.gnemirko.normalizer.dto.NormalizationRequest;
import com.gnemirko.normalizer.dto.NormalizationResponse;
import com.gnemirko.normalizer.service.NormalizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/normalize")
@RequiredArgsConstructor
public class NormalizationController {

    private final NormalizationService normalizationService;

    @PostMapping
    public ResponseEntity<NormalizationResponse> normalize(@Valid @RequestBody NormalizationRequest request) {
        return ResponseEntity.ok(normalizationService.normalize(request));
    }
}
