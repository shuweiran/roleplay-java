package com.roleplay.engine.controller;

import com.roleplay.engine.service.RouterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Round control endpoints.
 * Maps from Python api/routes_round.py.
 */
@RestController
@RequestMapping("/api/round")
public class RoundController {

    private final RouterService router;

    public RoundController(RouterService router) {
        this.router = router;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startRound(@RequestBody Map<String, Object> body) {
        String userInput = (String) body.getOrDefault("message",
            body.getOrDefault("text", ""));
        int turns = ((Number) body.getOrDefault("turns", 1)).intValue();
        RouterService.RoundResult result = router.runRound(userInput, null);
        return ResponseEntity.ok(Map.of(
            "status", result.status,
            "round", router.getRoundCount(),
            "agent_outputs", result.agentOutputs,
            "narration", result.integration.getOrDefault("narration", ""),
            "metrics", result.metrics
        ));
    }

    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollback(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("status", "rolled_back"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRoundStatus() {
        return ResponseEntity.ok(Map.of(
            "running", router.isRunning(),
            "round", router.getRoundCount()
        ));
    }
}
