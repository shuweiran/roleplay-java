package com.roleplay.engine.controller;

import com.roleplay.engine.model.Session;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation history endpoints.
 * Maps from Python api/routes_history.py.
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final Map<String, Session> savedSessions = new ConcurrentHashMap<>();

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        savedSessions.forEach((id, s) -> sessions.add(Map.of(
            "session_id", id,
            "message_count", s.getMessages().size(),
            "created_at", s.getCreatedAt(),
            "round_count", s.getRoundCount()
        )));
        sessions.sort((a, b) -> String.valueOf(b.get("created_at")).compareTo(String.valueOf(a.get("created_at"))));
        return ResponseEntity.ok(Map.of("sessions", sessions));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> listSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        savedSessions.forEach((id, s) -> list.add(Map.of(
            "id", id, "created_at", s.getCreatedAt(),
            "rounds", s.getRoundCount()
        )));
        return ResponseEntity.ok(Map.of("sessions", list));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSession(@PathVariable String id) {
        Session s = savedSessions.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(s.toMap());
    }

    @PostMapping("/load/{id}")
    public ResponseEntity<Map<String, Object>> loadSession(@PathVariable String id) {
        Session s = savedSessions.get(id);
        if (s == null) return ResponseEntity.ok(Map.of("status", "not_found"));
        return ResponseEntity.ok(Map.of("status", "loaded", "session_id", id));
    }

    /** Auto-save a session from MemoryStore. */
    public void saveSession(String id, Session session) {
        if (id != null && session != null) {
            savedSessions.put(id, session);
        }
    }
}
