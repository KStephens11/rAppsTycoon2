package com.rapptycoon.controller;

import com.rapptycoon.dto.DeployRequest;
import com.rapptycoon.dto.DeploymentResponse;
import com.rapptycoon.dto.TuneRequest;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.service.RappService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions/{code}/rapps")
public class RappController {

    private final RappService rappService;

    public RappController(RappService rappService) {
        this.rappService = rappService;
    }

    @PostMapping("/deploy")
    public ResponseEntity<DeploymentResponse> deploy(
            @PathVariable String code,
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @Valid @RequestBody DeployRequest request) {
        validateToken(token);
        DeploymentResponse response = rappService.deploy(code, token, request.templateId(), request.basestationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/tune")
    public ResponseEntity<DeploymentResponse> tune(
            @PathVariable String code,
            @PathVariable Long id,
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @Valid @RequestBody TuneRequest request) {
        validateToken(token);
        DeploymentResponse response = rappService.tune(code, token, id, request.threshold(), request.aggressiveness());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<DeploymentResponse> disable(
            @PathVariable String code,
            @PathVariable Long id,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        validateToken(token);
        DeploymentResponse response = rappService.disable(code, token, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/rollback")
    public ResponseEntity<DeploymentResponse> rollback(
            @PathVariable String code,
            @PathVariable Long id,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        validateToken(token);
        DeploymentResponse response = rappService.rollback(code, token, id);
        return ResponseEntity.ok(response);
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }
    }
}
