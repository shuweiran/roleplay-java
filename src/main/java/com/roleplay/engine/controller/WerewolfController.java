package com.roleplay.engine.controller;

import com.roleplay.engine.service.WerewolfService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Werewolf game endpoints — now backed by full WerewolfService game engine.
 */
@RestController
@RequestMapping("/api/werewolf")
public class WerewolfController {

    private final WerewolfService werewolfService;
    private final Map<String, String> playerSessions = new ConcurrentHashMap<>();
    private String currentSessionId = "";

    public WerewolfController(WerewolfService werewolfService) {
        this.werewolfService = werewolfService;
    }

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init(@RequestParam(defaultValue = "") String player_name,
                                                     @RequestParam(defaultValue = "") String human_players,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        // Support both query param and JSON body formats
        List<String> players = new ArrayList<>();
        String playerName = player_name;
        if (body != null && body.containsKey("players")) {
            @SuppressWarnings("unchecked")
            List<String> bodyPlayers = (List<String>) body.get("players");
            players.addAll(bodyPlayers);
        } else if (!human_players.isEmpty()) {
            players.addAll(Arrays.asList(human_players.split(",")));
        }
        if (!playerName.isEmpty() && !players.contains(playerName)) {
            players.add(0, playerName);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> customRoles = body != null
            ? (Map<String, String>) body.get("roles") : null;

        String sessionId = UUID.randomUUID().toString().substring(0, 12);
        currentSessionId = sessionId;
        players.forEach(p -> playerSessions.put(p, sessionId));

        Map<String, Object> state = werewolfService.initGame(sessionId, players, customRoles);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/night_action")
    public ResponseEntity<Map<String, Object>> nightAction(@RequestBody Map<String, String> body) {
        String player = body.getOrDefault("player", "");
        String action = body.getOrDefault("action", "");
        String target = body.getOrDefault("target", "");
        String sessionId = playerSessions.getOrDefault(player, currentSessionId);
        String result = werewolfService.recordNightAction(sessionId, player, action, target);
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/hunter_shoot")
    public ResponseEntity<Map<String, Object>> hunterShoot(@RequestBody Map<String, String> body) {
        String player = body.getOrDefault("player", "");
        String target = body.getOrDefault("target", "");
        String sessionId = playerSessions.getOrDefault(player, currentSessionId);
        String result = werewolfService.hunterShoot(sessionId, player, target);
        return ResponseEntity.ok(Map.of("result", result));
    }

    /** Admin: resolve night phase and transition to day. */
    @PostMapping("/resolve_night")
    public ResponseEntity<Map<String, Object>> resolveNight(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        return ResponseEntity.ok(werewolfService.resolveNight(sessionId));
    }

    @PostMapping("/vote")
    public ResponseEntity<Map<String, Object>> vote(@RequestBody Map<String, Object> body) {
        String player = (String) body.getOrDefault("player", "");
        String target = (String) body.getOrDefault("target", "");
        String sessionId = playerSessions.getOrDefault(player, currentSessionId);
        String result = werewolfService.castVote(sessionId, player, target);
        return ResponseEntity.ok(Map.of("result", result, "phase", "day_vote"));
    }

    /** Admin: resolve votes and transition to night. */
    @PostMapping("/resolve_vote")
    public ResponseEntity<Map<String, Object>> resolveVote(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        return ResponseEntity.ok(werewolfService.resolveVote(sessionId));
    }

    @PostMapping("/start_voting")
    public ResponseEntity<Map<String, Object>> startVoting(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        werewolfService.startVoting(sessionId);
        return ResponseEntity.ok(Map.of("phase", "day_vote"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(defaultValue = "") String player,
                                                          @RequestParam(defaultValue = "") String player_name) {
        String p = !player.isEmpty() ? player : player_name;
        String sessionId = playerSessions.getOrDefault(p, currentSessionId);
        if (sessionId.isEmpty()) {
            return ResponseEntity.ok(Map.of("game_over", true, "phase", "idle"));
        }
        return ResponseEntity.ok(werewolfService.getGame(sessionId).toMap(p));
    }
}
