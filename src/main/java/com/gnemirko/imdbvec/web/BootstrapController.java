package com.gnemirko.imdbvec.web;

import com.gnemirko.imdbvec.service.BootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bootstrap")
public class BootstrapController {

    private final BootstrapService bootstrapService;
    private final String expectedToken;

    public BootstrapController(BootstrapService bootstrapService,
                               @Value("${app.admin.bootstrap-token:}") String expectedToken) {
        this.bootstrapService = bootstrapService;
        this.expectedToken = expectedToken;
    }

    @PostMapping
    public ResponseEntity<String> triggerBootstrap(@RequestParam(name = "rebuildIndex", defaultValue = "true") boolean rebuildIndex,
                                                   @RequestHeader(value = "X-Bootstrap-Token", required = false) String token) {
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (token == null || !expectedToken.equals(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid bootstrap token");
            }
        }

        bootstrapService.runFullBootstrap(rebuildIndex);
        return ResponseEntity.accepted().body("Bootstrap started");
    }
}
