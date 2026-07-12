package com.roleplay.engine.controller;

import com.roleplay.engine.service.GeneratorService;
import com.roleplay.engine.service.RouterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scene CRUD endpoints.
 */
@RestController
@RequestMapping("/api/scenes")
public class SceneController {

    private final List<Map<String, Object>> scenes = new CopyOnWriteArrayList<>();
    private final GeneratorService generator;
    private final RouterService router;

    public SceneController(GeneratorService generator, RouterService router) {
        this.generator = generator;
        this.router = router;
        scenes.add(Map.of(
            "scene_id", "default",
            "name", "默认场景",
            "description", "一个普通的房间",
            "initial_agent_names", List.of("助手")
        ));
    }

    public List<Map<String, Object>> getAll() { return new ArrayList<>(scenes); }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(new ArrayList<>(scenes));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("scene_id", UUID.randomUUID().toString().substring(0, 8));
        scene.put("name", body.getOrDefault("name", "未命名场景"));
        scene.put("description", body.getOrDefault("description", ""));
        scene.put("keywords", body.getOrDefault("keywords", ""));
        scene.put("initial_agent_names", body.getOrDefault("initial_agent_names", List.of()));
        scenes.add(scene);
        return ResponseEntity.ok(scene);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        for (int i = 0; i < scenes.size(); i++) {
            if (id.equals(scenes.get(i).get("scene_id"))) {
                Map<String, Object> updated = new LinkedHashMap<>(scenes.get(i));
                body.forEach((k, v) -> { if (v != null) updated.put(k, v); });
                scenes.set(i, updated);
                return ResponseEntity.ok(updated);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        scenes.removeIf(s -> id.equals(s.get("scene_id")));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startScene(@PathVariable String id,
                                                           @RequestParam String agents,
                                                           @RequestParam(defaultValue = "") String me) {
        String[] agentNames = agents.split(",");
        List<com.roleplay.engine.core.Persona> personas = new ArrayList<>();
        for (String name : agentNames) {
            name = name.trim();
            if (!name.isEmpty()) {
                com.roleplay.engine.core.Persona p = new com.roleplay.engine.core.Persona(name);
                p.setPersonaDesc(name + "，一个角色");
                personas.add(p);
            }
        }
        if (personas.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "至少需要一个角色"));
        }
        String sessionId = UUID.randomUUID().toString().substring(0, 12);
        router.initSession(sessionId, personas, id, "free", "", "");
        Map<String, Object> result = new LinkedHashMap<>(router.getState());
        result.put("session_id", sessionId);
        result.put("mode", "free");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(generator.generateScene(body.getOrDefault("keywords", ""), ""));
    }
}
