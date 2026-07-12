package com.roleplay.engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multiplayer room management.
 * Maps from Python api/routes_room.py.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final Map<String, Map<String, Object>> rooms = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody Map<String, Object> body) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String host = (String) body.getOrDefault("host",
            body.getOrDefault("player_name", "unknown"));
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("code", code);
        room.put("mode", body.getOrDefault("mode", "free"));
        room.put("host", host);
        room.put("players", new ArrayList<>(List.of(host)));
        room.put("assignments", new HashMap<>());
        rooms.put(code, room);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> getRoom(@PathVariable String code) {
        Map<String, Object> room = rooms.get(code);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String code, @RequestBody Map<String, String> body) {
        Map<String, Object> room = rooms.get(code);
        if (room == null) return ResponseEntity.ok(Map.of("error", "房间不存在"));
        @SuppressWarnings("unchecked")
        List<String> players = (List<String>) room.get("players");
        String player = body.getOrDefault("player",
            body.getOrDefault("player_name", "unknown"));
        if (!players.contains(player)) players.add(player);
        return ResponseEntity.ok(Map.of("status", "joined", "players", players));
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String code, @RequestBody Map<String, String> body) {
        Map<String, Object> room = rooms.get(code);
        if (room == null) return ResponseEntity.ok(Map.of("error", "房间不存在"));
        @SuppressWarnings("unchecked")
        List<String> players = (List<String>) room.get("players");
        players.remove(body.getOrDefault("player", ""));
        return ResponseEntity.ok(Map.of("status", "left"));
    }

    @PostMapping("/{code}/assign")
    public ResponseEntity<?> assignRoles(@PathVariable String code, @RequestBody Map<String, Object> body) {
        Map<String, Object> room = rooms.get(code);
        if (room == null) return ResponseEntity.ok(Map.of("error", "房间不存在"));
        @SuppressWarnings("unchecked")
        Map<String, String> assignments = (Map<String, String>) body.get("assignments");
        room.put("assignments", assignments);
        return ResponseEntity.ok(Map.of("status", "assigned"));
    }
}
