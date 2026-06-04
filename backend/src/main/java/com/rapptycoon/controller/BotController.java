package com.rapptycoon.controller;

import com.rapptycoon.dto.AddBotsRequest;
import com.rapptycoon.dto.AddBotsResponse;
import com.rapptycoon.service.BotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class BotController {

    private final BotService botService;

    public BotController(BotService botService) {
        this.botService = botService;
    }

    @PostMapping("/{code}/bots")
    public ResponseEntity<AddBotsResponse> addBots(
            @PathVariable String code,
            @RequestHeader("X-Session-Token") String token,
            @Valid @RequestBody AddBotsRequest request) {
        AddBotsResponse response = botService.addBots(code, token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
