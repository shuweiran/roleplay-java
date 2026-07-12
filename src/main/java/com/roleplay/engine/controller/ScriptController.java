package com.roleplay.engine.controller;

import com.roleplay.engine.service.ScriptGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Script murder mystery game endpoints.
 */
@RestController
@RequestMapping("/api/script")
public class ScriptController {

    private final ScriptGameService scriptGameService;
    private final Map<String, String> playerSessions = new ConcurrentHashMap<>();
    private String currentSessionId = "";

    public ScriptController(ScriptGameService scriptGameService) {
        this.scriptGameService = scriptGameService;
    }

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> players = (List<String>) body.getOrDefault("players", List.of());
        String theme = (String) body.getOrDefault("theme", "默认主题");
        String sessionId = UUID.randomUUID().toString().substring(0, 12);
        currentSessionId = sessionId;
        players.forEach(p -> playerSessions.put(p, sessionId));
        return ResponseEntity.ok(scriptGameService.initGame(sessionId, theme, players));
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, String> body) {
        String player = body.getOrDefault("player", "");
        String location = body.getOrDefault("location", "");
        String sessionId = playerSessions.getOrDefault(player, currentSessionId);
        return ResponseEntity.ok(scriptGameService.search(sessionId, player, location));
    }

    @PostMapping("/start_discussion")
    public ResponseEntity<Map<String, Object>> startDiscussion(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        scriptGameService.startDiscussion(sessionId);
        return ResponseEntity.ok(Map.of("phase", "discussion"));
    }

    @PostMapping("/start_voting")
    public ResponseEntity<Map<String, Object>> startVoting(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        scriptGameService.startVoting(sessionId);
        return ResponseEntity.ok(Map.of("phase", "vote"));
    }

    @PostMapping("/vote")
    public ResponseEntity<Map<String, Object>> vote(@RequestBody Map<String, String> body) {
        String player = body.getOrDefault("player", "");
        String suspect = body.getOrDefault("suspect", "");
        String sessionId = playerSessions.getOrDefault(player, currentSessionId);
        String result = scriptGameService.castVote(sessionId, player, suspect);
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        return ResponseEntity.ok(scriptGameService.resolveVote(sessionId));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(defaultValue = "") String player) {
        String sessionId = playerSessions.getOrDefault(player, currentSessionId);
        if (sessionId.isEmpty()) {
            return ResponseEntity.ok(Map.of("phase", "idle"));
        }
        ScriptGameService.ScriptGame game = scriptGameService.getGame(sessionId);
        if (game == null) return ResponseEntity.ok(Map.of("phase", "not_found"));
        return ResponseEntity.ok(game.toMap(player));
    }
}
