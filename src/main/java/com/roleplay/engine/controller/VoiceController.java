package com.roleplay.engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Voice loop control endpoints.
 * Maps from Python api/routes_voice.py.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private boolean voiceLoopRunning = false;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "running", voiceLoopRunning,
            "engine", "edge",
            "auto_start", false
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        voiceLoopRunning = true;
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        voiceLoopRunning = false;
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }
}
