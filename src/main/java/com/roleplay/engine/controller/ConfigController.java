package com.roleplay.engine.controller;

import com.roleplay.engine.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Configuration endpoints — API Key, language, models, voice config.
 * Maps from Python api/routes_config.py.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AppConfig appConfig;

    // In-memory config overrides (replaces api_key.json)
    private final Map<String, Object> runtimeConfig = new HashMap<>();

    public ConfigController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return key;
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    @GetMapping("/apikey")
    public ResponseEntity<Map<String, Object>> getApiKey() {
        String key = runtimeConfig.containsKey("api_key")
            ? (String) runtimeConfig.get("api_key")
            : appConfig.getLlm().getApiKey();
        return ResponseEntity.ok(Map.of("api_key", maskKey(key), "configured", !key.isEmpty()));
    }

    @PostMapping("/apikey")
    public ResponseEntity<Void> setApiKey(@RequestBody Map<String, String> body) {
        runtimeConfig.put("api_key", body.getOrDefault("api_key", ""));
        appConfig.getLlm().setApiKey(body.getOrDefault("api_key", ""));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/language")
    public ResponseEntity<Map<String, String>> getLanguage() {
        return ResponseEntity.ok(Map.of("language", appConfig.getMode().getLanguage()));
    }

    @PostMapping("/language")
    public ResponseEntity<Void> setLanguage(@RequestBody Map<String, String> body) {
        appConfig.getMode().setLanguage(body.getOrDefault("language", "zh"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/models")
    public ResponseEntity<List<Map<String, String>>> getModels() {
        return ResponseEntity.ok(List.of(
            Map.of("id", "deepseek-v4-flash", "name", "DeepSeek V4 Flash"),
            Map.of("id", "deepseek-v4-pro", "name", "DeepSeek V4 Pro"),
            Map.of("id", "gpt-4o-mini", "name", "GPT-4o Mini"),
            Map.of("id", "gpt-4o", "name", "GPT-4o")
        ));
    }

    @GetMapping("/voice")
    public ResponseEntity<Map<String, Object>> getVoiceConfig() {
        return ResponseEntity.ok(Map.of(
            "enabled", true, "engine", "edge",
            "auto_select", true
        ));
    }

    @PostMapping("/voice")
    public ResponseEntity<Void> setVoiceConfig(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok().build();
    }
}
