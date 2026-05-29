package com.rapptycoon.controller;

import com.rapptycoon.dto.ImpactDto;
import com.rapptycoon.dto.RappTemplateResponse;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.service.PlayerService;
import com.rapptycoon.service.RappCatalogueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rapps")
public class CatalogueController {

    private final RappCatalogueService rappCatalogueService;
    private final PlayerService playerService;

    public CatalogueController(RappCatalogueService rappCatalogueService, PlayerService playerService) {
        this.rappCatalogueService = rappCatalogueService;
        this.playerService = playerService;
    }

    @GetMapping("/catalogue")
    public ResponseEntity<Map<String, List<RappTemplateResponse>>> getCatalogue(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }

        playerService.validateToken(token);

        List<RappTemplate> templates = rappCatalogueService.getCatalogue();
        List<RappTemplateResponse> responses = templates.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(Map.of("rapps", responses));
    }

    private RappTemplateResponse toResponse(RappTemplate template) {
        ImpactDto impact = new ImpactDto(
                template.getImpactHealth(),
                template.getImpactCustomerExperience(),
                template.getImpactCost(),
                template.getImpactEnergyEfficiency(),
                template.getImpactAutomationReliability(),
                template.getImpactSlaCompliance()
        );

        return new RappTemplateResponse(
                template.getId(),
                template.getName(),
                template.getPurpose(),
                template.getCost(),
                template.getBenefit(),
                template.getRisk(),
                template.getConfidence(),
                template.getSideEffects(),
                impact
        );
    }
}
