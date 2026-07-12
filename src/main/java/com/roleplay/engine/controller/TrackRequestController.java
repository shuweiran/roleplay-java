package com.roleplay.engine.controller;

import com.roleplay.engine.service.TrackRequestService;
import com.roleplay.engine.service.TrackRequestService.TrackChangeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Track change request endpoints.
 * Maps from Python api/routes_track.py + core/track_request.py.
 */
@RestController
@RequestMapping("/api/track")
public class TrackRequestController {

    private final TrackRequestService trackRequestService;
    private String currentSessionId = "";

    public TrackRequestController(TrackRequestService trackRequestService) {
        this.trackRequestService = trackRequestService;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> submitRequest(@RequestBody Map<String, Object> body) {
        TrackChangeRequest req = trackRequestService.submitRequest(
            (String) body.getOrDefault("session_id", currentSessionId),
            (String) body.getOrDefault("agent", ""),
            (String) body.getOrDefault("target_agent", ""),
            (String) body.getOrDefault("from_track", "main"),
            (String) body.getOrDefault("to_track", "main"),
            (String) body.getOrDefault("from_mode", "merged"),
            (String) body.getOrDefault("to_mode", "isolated"),
            (String) body.getOrDefault("reason", ""));
        return ResponseEntity.ok(req.toMap());
    }

    @PostMapping("/requests/approve")
    public ResponseEntity<Map<String, Object>> approveRequest(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        trackRequestService.approveRequest(sessionId,
            body.getOrDefault("request_id", ""),
            body.getOrDefault("note", ""));
        return ResponseEntity.ok(Map.of("status", "approved"));
    }

    @PostMapping("/requests/reject")
    public ResponseEntity<Map<String, Object>> rejectRequest(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("session_id", currentSessionId);
        trackRequestService.rejectRequest(sessionId,
            body.getOrDefault("request_id", ""),
            body.getOrDefault("note", ""));
        return ResponseEntity.ok(Map.of("status", "rejected"));
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> listRequests(
            @RequestParam(defaultValue = "") String session_id,
            @RequestParam(defaultValue = "") String filter) {
        String sid = session_id.isEmpty() ? currentSessionId : session_id;
        List<TrackChangeRequest> requests;
        if ("pending".equals(filter)) {
            requests = trackRequestService.getPendingRequests(sid);
        } else {
            requests = trackRequestService.getAllRequests(sid);
        }
        return ResponseEntity.ok(Map.of("requests",
            requests.stream().map(TrackChangeRequest::toMap).collect(Collectors.toList())));
    }

    @PostMapping("/requests/evaluate")
    public ResponseEntity<List<Map<String, Object>>> evaluateNeeds(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> agentNames = (List<String>) body.getOrDefault("agents", List.of());
        @SuppressWarnings("unchecked")
        List<String> goals = (List<String>) body.getOrDefault("goals", List.of());
        @SuppressWarnings("unchecked")
        Map<String, String> modes = (Map<String, String>) body.getOrDefault("current_modes", Map.of());
        List<Map<String, Object>> suggestions = trackRequestService.evaluateAgentNeeds(
            (String) body.getOrDefault("session_id", currentSessionId),
            (String) body.getOrDefault("scene", ""),
            agentNames, goals, modes,
            (String) body.getOrDefault("plot_summary", ""));
        return ResponseEntity.ok(suggestions);
    }
}
