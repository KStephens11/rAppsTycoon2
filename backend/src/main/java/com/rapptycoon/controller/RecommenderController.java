package com.rapptycoon.controller;

import com.rapptycoon.dto.RecommendationsResponse;
import com.rapptycoon.service.RecommenderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class RecommenderController {

    private final RecommenderService recommenderService;

    public RecommenderController(RecommenderService recommenderService) {
        this.recommenderService = recommenderService;
    }

    @GetMapping("/{code}/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(
            @PathVariable String code,
            @RequestHeader("X-Session-Token") String token) {
        RecommendationsResponse response = recommenderService.getRecommendations(code, token);
        return ResponseEntity.ok(response);
    }
}
