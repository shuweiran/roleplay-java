package com.roleplay.engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Invite-code authentication endpoints.
 * Maps from Python api/routes_auth.py + services/invite_service.py.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Map<String, Boolean> inviteCodes = new ConcurrentHashMap<>();
    private final Set<String> activeTokens = ConcurrentHashMap.newKeySet();

    public AuthController() {
        inviteCodes.put("DEFAULT2024", true);
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "");
        if (inviteCodes.containsKey(code) && inviteCodes.get(code)) {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            activeTokens.add(token);
            return ResponseEntity.ok(Map.of(
                "token", token, "user", "player",
                "message", "验证成功"
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "无效的邀请码"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        if (activeTokens.contains(token)) {
            return ResponseEntity.ok(Map.of(
                "user", "player", "authenticated", true
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "未认证"));
    }

    @PostMapping("/admin/generate")
    public ResponseEntity<Map<String, String>> generateCode() {
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        inviteCodes.put(code, true);
        return ResponseEntity.ok(Map.of("code", code));
    }

    @GetMapping("/admin/list")
    public ResponseEntity<List<Map<String, Object>>> listCodes() {
        List<Map<String, Object>> codes = new ArrayList<>();
        inviteCodes.forEach((code, active) ->
            codes.add(Map.of("code", code, "active", active, "uses", 0)));
        return ResponseEntity.ok(codes);
    }

    @PostMapping("/admin/deactivate")
    public ResponseEntity<Void> deactivate(@RequestBody Map<String, String> body) {
        inviteCodes.put(body.getOrDefault("code", ""), false);
        return ResponseEntity.ok().build();
    }
}
