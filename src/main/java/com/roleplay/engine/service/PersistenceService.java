package com.roleplay.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.roleplay.engine.core.Message;
import com.roleplay.engine.core.Persona;
import com.roleplay.engine.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based JSON persistence — atomic writes, character/scene stores.
 * Maps from Python services/persistence.py.
 */
@Service
public class PersistenceService {
    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final ObjectMapper mapper;
    private final Path dataDir;

    // In-memory caches
    private final Map<String, Map<String, Object>> characters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> scenes = new ConcurrentHashMap<>();

    public PersistenceService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.dataDir = Path.of(System.getProperty("user.home"),
            ".openclaw", "workspace", "roleplay-java", "data");
        try {
            Files.createDirectories(dataDir.resolve("characters"));
            Files.createDirectories(dataDir.resolve("scenes"));
            Files.createDirectories(dataDir.resolve("sessions"));
            loadAll();
        } catch (IOException e) {
            log.warn("Cannot create data directory: {}", e.getMessage());
        }
    }

    // ── Atomic file write ──────────────────────────────────────

    private void atomicWrite(Path path, Object data) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    // ── Character store ────────────────────────────────────────

    public List<Map<String, Object>> listCharacters() {
        return new ArrayList<>(characters.values());
    }

    public Optional<Map<String, Object>> getCharacter(String name) {
        return Optional.ofNullable(characters.get(name));
    }

    public Map<String, Object> saveCharacter(Map<String, Object> ch) {
        String name = (String) ch.getOrDefault("name", "unknown");
        Map<String, Object> clean = new LinkedHashMap<>(ch);
        characters.put(name, clean);
        try {
            atomicWrite(dataDir.resolve("characters").resolve(name + ".json"), clean);
        } catch (IOException e) {
            log.warn("Failed to save character {}: {}", name, e.getMessage());
        }
        return clean;
    }

    public void deleteCharacter(String name) {
        characters.remove(name);
        try {
            Files.deleteIfExists(dataDir.resolve("characters").resolve(name + ".json"));
        } catch (IOException e) {
            log.warn("Failed to delete character file: {}", e.getMessage());
        }
    }

    // ── Scene store ────────────────────────────────────────────

    public List<Map<String, Object>> listScenes() {
        return new ArrayList<>(scenes.values());
    }

    public Optional<Map<String, Object>> getScene(String id) {
        return Optional.ofNullable(scenes.get(id));
    }

    public Map<String, Object> saveScene(Map<String, Object> scene) {
        String id = (String) scene.getOrDefault("scene_id",
            UUID.randomUUID().toString().substring(0, 8));
        Map<String, Object> clean = new LinkedHashMap<>(scene);
        clean.putIfAbsent("scene_id", id);
        scenes.put(id, clean);
        try {
            atomicWrite(dataDir.resolve("scenes").resolve(id + ".json"), clean);
        } catch (IOException e) {
            log.warn("Failed to save scene {}: {}", id, e.getMessage());
        }
        return clean;
    }

    public void deleteScene(String id) {
        scenes.remove(id);
        try {
            Files.deleteIfExists(dataDir.resolve("scenes").resolve(id + ".json"));
        } catch (IOException e) {
            log.warn("Failed to delete scene file: {}", e.getMessage());
        }
    }

    // ── Session persistence ────────────────────────────────────

    public void saveSession(Session session) {
        try {
            atomicWrite(dataDir.resolve("sessions").resolve(session.getSessionId() + ".json"),
                session.toMap());
        } catch (IOException e) {
            log.warn("Failed to save session: {}", e.getMessage());
        }
    }

    public Optional<Session> loadSession(String sessionId) {
        Path path = dataDir.resolve("sessions").resolve(sessionId + ".json");
        if (!Files.exists(path)) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(path.toFile(), Map.class);
            return Optional.of(new Session(sessionId)); // Simplified restore
        } catch (IOException e) {
            log.warn("Failed to load session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<String> listSessions() {
        List<String> ids = new ArrayList<>();
        Path dir = dataDir.resolve("sessions");
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .forEach(ids::add);
            } catch (IOException e) {
                log.warn("Failed to list sessions: {}", e.getMessage());
            }
        }
        ids.sort(Collections.reverseOrder());
        return ids;
    }

    // ── Bootstrap ──────────────────────────────────────────────

    private void loadAll() {
        loadDir("characters", characters);
        loadDir("scenes", scenes);
    }

    @SuppressWarnings("unchecked")
    private void loadDir(String sub, Map<String, Map<String, Object>> target) {
        Path dir = dataDir.resolve(sub);
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    Map<String, Object> data = mapper.readValue(p.toFile(), Map.class);
                    String key = (String) data.getOrDefault("name",
                        data.getOrDefault("scene_id", p.getFileName().toString()));
                    target.put(key, data);
                } catch (IOException e) {
                    log.warn("Failed to load {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list {}: {}", sub, e.getMessage());
        }
    }
}
