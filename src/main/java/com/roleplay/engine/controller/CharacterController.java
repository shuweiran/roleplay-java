package com.roleplay.engine.controller;

import com.roleplay.engine.core.Persona;
import com.roleplay.engine.service.GeneratorService;
import com.roleplay.engine.service.MemoryStore;
import com.roleplay.engine.service.RouterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Character CRUD endpoints.
 * Maps from Python api/routes_characters.py.
 */
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final List<Map<String, Object>> characters = new CopyOnWriteArrayList<>();
    private final GeneratorService generator;

    public CharacterController(GeneratorService generator) {
        this.generator = generator;
        // Seed defaults
        characters.add(Map.of("name", "助手", "persona", "温柔体贴的助手",
            "voice", "温和", "background", "一直在你身边"));
    }

    public List<Map<String, Object>> getAll() { return new ArrayList<>(characters); }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(new ArrayList<>(characters));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> ch = new LinkedHashMap<>();
        ch.put("name", body.getOrDefault("name", "未命名"));
        ch.put("persona", body.getOrDefault("persona", ""));
        ch.put("voice", body.getOrDefault("voice", ""));
        ch.put("background", body.getOrDefault("background", ""));
        characters.add(ch);
        return ResponseEntity.ok(ch);
    }

    @PutMapping("/{name}")
    public ResponseEntity<?> update(@PathVariable String name, @RequestBody Map<String, Object> body) {
        for (int i = 0; i < characters.size(); i++) {
            if (name.equals(characters.get(i).get("name"))) {
                Map<String, Object> updated = new LinkedHashMap<>(characters.get(i));
                body.forEach((k, v) -> { if (v != null) updated.put(k, v); });
                characters.set(i, updated);
                return ResponseEntity.ok(updated);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        characters.removeIf(c -> name.equals(c.get("name")));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(generator.generateCharacter(body.getOrDefault("keywords", "")));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Map<String, Object>>> batch(@RequestBody List<Map<String, Object>> batch) {
        for (Map<String, Object> ch : batch) {
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("name", ch.getOrDefault("name", "未命名"));
            clean.put("persona", ch.getOrDefault("persona", ""));
            clean.put("voice", ch.getOrDefault("voice", ""));
            clean.put("background", ch.getOrDefault("background", ""));
            characters.add(clean);
        }
        return ResponseEntity.ok(new ArrayList<>(characters));
    }
}
