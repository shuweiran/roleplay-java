package com.roleplay.engine.controller;

import com.roleplay.engine.core.Message;
import com.roleplay.engine.core.Persona;
import com.roleplay.engine.service.RouterService;
import com.roleplay.engine.service.ScriptService;
import com.roleplay.engine.service.PrivateChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main session endpoints — the primary conversational interface.
 * Maps from Python api/routes_session.py (700+ lines, the largest route file).
 */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final RouterService router;
    private final ScriptService scriptService;
    private final PrivateChatService privateChatService;
    private final CharacterController characterController;
    private final SceneController sceneController;
    private final Map<String, RouterService> sessions = new ConcurrentHashMap<>();

    public SessionController(RouterService router, ScriptService scriptService,
                             PrivateChatService privateChatService,
                             CharacterController characterController,
                             SceneController sceneController) {
        this.router = router;
        this.scriptService = scriptService;
        this.privateChatService = privateChatService;
        this.characterController = characterController;
        this.sceneController = sceneController;
    }

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState() {
        Map<String, Object> state = new LinkedHashMap<>(router.getState());
        state.put("characters", characterController.getAll());
        state.put("scenes", sceneController.getAll());
        return ResponseEntity.ok(state);
    }

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initialize(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> characterList = (List<Map<String, String>>) body.getOrDefault("characters", List.of());
        List<Persona> personas = new ArrayList<>();
        for (Map<String, String> ch : characterList) {
            Persona p = new Persona(ch.getOrDefault("name", "未知"));
            p.setPersonaDesc(ch.getOrDefault("persona", ""));
            p.setVoice(ch.getOrDefault("voice", ""));
            p.setBackground(ch.getOrDefault("background", ""));
            personas.add(p);
        }
        if (personas.isEmpty()) {
            personas.add(new Persona("助手", "一个友好的助手"));
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 12);
        router.initSession(sessionId, personas,
            (String) body.getOrDefault("scene", "默认场景"),
            (String) body.getOrDefault("mode", "free"),
            (String) body.getOrDefault("protagonist", ""),
            (String) body.getOrDefault("director_character", ""));
        sessions.put(sessionId, router);

        return ResponseEntity.ok(Map.of(
            "session_id", sessionId,
            "status", "initialized",
            "agents", personas.stream().map(Persona::getName).toList()
        ));
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", body.getOrDefault("text", ""));
        RouterService.RoundResult result = router.runRound(message, null);
        return ResponseEntity.ok(Map.of(
            "status", result.status,
            "agent_outputs", result.agentOutputs,
            "narration", result.integration.getOrDefault("narration", ""),
            "reasoning", result.reasoning
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        router.stop();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @PostMapping("/auto")
    public ResponseEntity<Map<String, Object>> startAuto(@RequestBody Map<String, Object> body) {
        int rounds = ((Number) body.getOrDefault("rounds", 3)).intValue();
        List<RouterService.RoundResult> results = router.runAutoRounds(rounds);
        return ResponseEntity.ok(Map.of(
            "rounds", results.size(),
            "last_status", results.isEmpty() ? "" : results.get(results.size() - 1).status
        ));
    }

    @PostMapping("/mode")
    public ResponseEntity<Map<String, Object>> setMode(@RequestBody Map<String, String> body) {
        router.setMode(body.getOrDefault("mode", "free"));
        String protagonist = body.getOrDefault("protagonist",
            body.getOrDefault("protagonist", ""));
        if (!protagonist.isEmpty()) router.setProtagonist(protagonist);
        String director = body.getOrDefault("director_character",
            body.getOrDefault("director", ""));
        if (!director.isEmpty()) router.setDirectorCharacter(director);
        return ResponseEntity.ok(Map.of("mode", router.getMode()));
    }

    @GetMapping("/mode")
    public ResponseEntity<Map<String, String>> getMode() {
        return ResponseEntity.ok(Map.of("mode", router.getMode()));
    }

    @PostMapping("/goals")
    public ResponseEntity<Map<String, Object>> setGoals(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> goals = (List<String>) body.getOrDefault("goals", List.of());
        router.setGoals(goals);
        return ResponseEntity.ok(Map.of("goals", goals));
    }

    @GetMapping("/goals")
    public ResponseEntity<Map<String, Object>> getGoals() {
        return ResponseEntity.ok(Map.of("goals", router.getGoals()));
    }

    @PostMapping("/agents")
    public ResponseEntity<Map<String, Object>> addAgent(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "新角色");
        String persona = body.getOrDefault("persona", "");
        router.addAgent(name, new Persona(name, persona));
        return ResponseEntity.ok(Map.of("status", "added", "name", name));
    }

    @DeleteMapping("/agents/{name}")
    public ResponseEntity<Map<String, Object>> removeAgent(@PathVariable String name) {
        router.removeAgent(name);
        return ResponseEntity.ok(Map.of("status", "removed", "name", name));
    }

    // ── Voice toggle (inline in routes_session.py in Python) ──

    @GetMapping("/voice/toggle")
    public ResponseEntity<Map<String, Boolean>> getVoiceToggle() {
        return ResponseEntity.ok(Map.of("enabled", true));
    }

    @PostMapping("/voice/toggle")
    public ResponseEntity<Map<String, Boolean>> setVoiceToggle(@RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(Map.of("enabled", body.getOrDefault("enabled", true)));
    }

    @PostMapping("/script/generate")
    public ResponseEntity<Map<String, Object>> generateScript(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> characters = (List<String>) body.getOrDefault("characters", List.of());
        Map<String, Object> script = scriptService.generateScript(
            (String) body.getOrDefault("theme", "默认主题"),
            characters);
        return ResponseEntity.ok(script);
    }

    @PostMapping("/private_chat/request")
    public ResponseEntity<Map<String, Object>> requestPrivateChat(@RequestBody Map<String, String> body) {
        String from = body.getOrDefault("from", "");
        String to = body.getOrDefault("to", "");
        String message = body.getOrDefault("message", "");
        return ResponseEntity.ok(privateChatService.requestChat(from, to, message));
    }

    @PostMapping("/private_chat/reply")
    public ResponseEntity<Map<String, Object>> replyPrivateChat(@RequestBody Map<String, String> body) {
        String from = body.getOrDefault("from", "");
        String to = body.getOrDefault("to", "");
        boolean accept = Boolean.parseBoolean(body.getOrDefault("accept", "true"));
        return ResponseEntity.ok(privateChatService.reply(from, to, accept));
    }

    @PostMapping("/private_chat/send")
    public ResponseEntity<Map<String, Object>> sendPrivateChat(@RequestBody Map<String, String> body) {
        String from = body.getOrDefault("from", "");
        String to = body.getOrDefault("to", "");
        String content = body.getOrDefault("content", "");
        return ResponseEntity.ok(privateChatService.sendMessage(from, to, content));
    }
}
