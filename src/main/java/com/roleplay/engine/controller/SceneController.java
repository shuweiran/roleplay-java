package com.roleplay.engine.controller;

import com.roleplay.engine.service.GeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scene CRUD endpoints.
 * Maps from Python api/routes_scenes.py.
 */
@RestController
@RequestMapping("/api/scenes")
public class SceneController {

    private final List<Map<String, Object>> scenes = new CopyOnWriteArrayList<>();
    private final GeneratorService generator;

    public SceneController(GeneratorService generator) {
        this.generator = generator;
        scenes.add(Map.of(
            "scene_id", "default",
            "name", "默认场景",
            "description", "一个普通的房间",
            "initial_agent_names", List.of("助手")
        ));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(new ArrayList<>(scenes));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("scene_id", UUID.randomUUID().toString().substring(0, 8));
        scene.put("name", body.getOrDefault("name", "新场景"));
        scene.put("description", body.getOrDefault("description", ""));
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

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(generator.generateScene(
            body.getOrDefault("keywords", ""),
            body.getOrDefault("current_scene", "")));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String id) {
        for (Map<String, Object> scene : scenes) {
            if (id.equals(scene.get("scene_id"))) {
                return ResponseEntity.ok(Map.of(
                    "status", "started", "scene", scene,
                    "mode", "free"
                ));
            }
        }
        return ResponseEntity.ok(Map.of("status", "scene_not_found"));
    }

    @PostMapping("/{id}/enter")
    public ResponseEntity<Map<String, Object>> enter(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("status", "entered", "scene_id", id));
    }
}
