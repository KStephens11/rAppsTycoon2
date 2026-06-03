package com.rapptycoon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapptycoon.dto.*;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.GameSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RecommenderService {

    private static final Logger log = LoggerFactory.getLogger(RecommenderService.class);
    private static final int PROCESS_TIMEOUT_SECONDS = 10;

    private final GameSessionRepository gameSessionRepository;
    private final PlayerService playerService;
    private final BasestationService basestationService;
    private final RappCatalogueService rappCatalogueService;
    private final ObjectMapper objectMapper;

    @Value("${recommender.python.path:python3}")
    private String pythonPath;

    @Value("${recommender.script.path:../bot-player/recommend.py}")
    private String scriptPath;

    public RecommenderService(GameSessionRepository gameSessionRepository,
                              PlayerService playerService,
                              BasestationService basestationService,
                              RappCatalogueService rappCatalogueService,
                              ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerService = playerService;
        this.basestationService = basestationService;
        this.rappCatalogueService = rappCatalogueService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get AI recommendations for the requesting player.
     *
     * @param sessionCode the session code
     * @param token       the player's session token
     * @return the top-ranked recommendation or null
     */
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(String sessionCode, String token) {
        // Validate token and get player
        Player player = playerService.validateToken(token);

        // Find and validate session
        GameSession session = gameSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new SessionNotFoundException(sessionCode));

        // Verify player belongs to this session
        if (!player.getSessionId().equals(session.getId())) {
            throw new InvalidStateException("Player is not a member of this session");
        }

        // Validate session is ACTIVE
        if (session.getState() != GameSessionState.ACTIVE) {
            throw new InvalidStateException("Session is not in ACTIVE state");
        }

        // Gather player's game state
        Map<String, Object> gameState = buildGameState(player);

        // Invoke Python strategy module
        RecommendationDto recommendation = invokeStrategyModule(gameState);

        return new RecommendationResponse(recommendation);
    }

    private Map<String, Object> buildGameState(Player player) {
        // Get player's basestations with full state
        List<BasestationStateDto> basestations = basestationService.getPlayerBasestations(player.getId());

        // Get catalogue
        List<RappTemplate> templates = rappCatalogueService.getCatalogue();
        List<Map<String, Object>> catalogue = templates.stream()
                .map(this::templateToMap)
                .toList();

        // Build game state matching Python's expected format
        List<Map<String, Object>> basestationMaps = basestations.stream()
                .map(this::basestationToMap)
                .toList();

        Map<String, Object> gameState = new LinkedHashMap<>();
        gameState.put("basestations", basestationMaps);
        gameState.put("money", player.getScoreMoney().doubleValue());
        gameState.put("catalogue", catalogue);
        gameState.put("difficulty", player.getDifficulty() != null ? player.getDifficulty() : "MEDIUM");

        return gameState;
    }

    private Map<String, Object> basestationToMap(BasestationStateDto bs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", bs.id());
        map.put("name", bs.name());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("health", bs.metrics().health().doubleValue());
        metrics.put("customerExperience", bs.metrics().customerExperience().doubleValue());
        metrics.put("cost", bs.metrics().cost().doubleValue());
        metrics.put("energyEfficiency", bs.metrics().energyEfficiency().doubleValue());
        metrics.put("automationReliability", bs.metrics().automationReliability().doubleValue());
        metrics.put("slaCompliance", bs.metrics().slaCompliance().doubleValue());
        map.put("metrics", metrics);

        List<Map<String, Object>> deployedRapps = bs.deployedRapps().stream()
                .map(d -> {
                    Map<String, Object> rapp = new LinkedHashMap<>();
                    rapp.put("id", d.id());
                    rapp.put("templateId", d.templateId());
                    rapp.put("status", d.status());
                    rapp.put("version", d.version());
                    return rapp;
                })
                .toList();
        map.put("deployedRapps", deployedRapps);

        List<Map<String, Object>> activeEvents = bs.activeEvents().stream()
                .map(e -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("id", e.id());
                    event.put("eventType", e.eventType());
                    event.put("severity", e.severity());
                    event.put("description", e.description());
                    event.put("escalationLevel", e.escalationLevel());
                    return event;
                })
                .toList();
        map.put("activeEvents", activeEvents);

        return map;
    }

    private Map<String, Object> templateToMap(RappTemplate template) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", template.getId());
        map.put("name", template.getName());
        map.put("purpose", template.getPurpose());
        map.put("cost", template.getCost().doubleValue());
        map.put("benefit", template.getBenefit());
        map.put("risk", template.getRisk().doubleValue());
        map.put("confidence", template.getConfidence().doubleValue());
        map.put("sideEffects", template.getSideEffects());
        return map;
    }

    private RecommendationDto invokeStrategyModule(Map<String, Object> gameState) {
        try {
            String jsonInput = objectMapper.writeValueAsString(gameState);

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Write game state to stdin
            try (OutputStream os = process.getOutputStream()) {
                os.write(jsonInput.getBytes());
                os.flush();
            }

            // Wait for process to complete
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Python recommender process timed out after {} seconds", PROCESS_TIMEOUT_SECONDS);
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String stderr = readStream(process.getErrorStream());
                log.error("Python recommender process failed with exit code {}: {}", exitCode, stderr);
                return null;
            }

            // Read stdout
            String output = readStream(process.getInputStream());
            if (output.isBlank()) {
                log.warn("Python recommender returned empty output");
                return null;
            }

            // Parse output
            Map<String, Object> result = objectMapper.readValue(output, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recs = (List<Map<String, Object>>) result.get("recommendations");

            if (recs == null) {
                return null;
            }

            return recs.stream()
                    .findFirst()
                    .map(this::mapToRecommendationDto)
                    .orElse(null);

        } catch (IOException e) {
            log.error("Failed to invoke Python recommender: {}", e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Python recommender invocation interrupted", e);
            return null;
        }
    }

    private RecommendationDto mapToRecommendationDto(Map<String, Object> map) {
        String action = (String) map.get("action");
        Long rappTemplateId = map.get("rappTemplateId") != null
                ? ((Number) map.get("rappTemplateId")).longValue() : null;
        Long deploymentId = map.get("deploymentId") != null
                ? ((Number) map.get("deploymentId")).longValue() : null;
        Long basestationId = map.get("basestationId") != null
                ? ((Number) map.get("basestationId")).longValue() : null;
        double confidence = map.get("confidence") != null
                ? ((Number) map.get("confidence")).doubleValue() : 0.0;
        String reasoning = (String) map.get("reasoning");

        return new RecommendationDto(action, rappTemplateId, deploymentId, basestationId, confidence, reasoning);
    }

    private String readStream(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
