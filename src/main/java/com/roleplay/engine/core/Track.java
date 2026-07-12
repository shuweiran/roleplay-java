package com.roleplay.engine.core;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A single conversation track — the core isolation primitive.
 *
 * <p>Tracks control which agents share context and in what mode:
 * <ul>
 *   <li><b>MERGED</b> — all active agents share the same context, can directly interact</li>
 *   <li><b>WEAK</b> — primary agent active, others are silent listeners</li>
 *   <li><b>ISOLATED</b> — each agent operates in complete isolation</li>
 * </ul>
 *
 * Maps from Python {@code models/domain.py → Track}.
 */
public class Track {

    public enum Mode {
        MERGED,
        WEAK,
        ISOLATED
    }

    private String id;
    private List<String> agents = new ArrayList<>();
    private Map<String, String> agentActions = new HashMap<>();  // name → active|silent|offline
    private Mode mode = Mode.MERGED;
    private String color = "#4CAF50";
    private String label = "主线";
    private Instant createdAt = Instant.now();

    public Track() {}

    public Track(String id, Mode mode) {
        this.id = id;
        this.mode = mode;
    }

    // ── Builder-style constructors ─────────────────────────────

    public Track withAgents(List<String> agents) {
        this.agents = new ArrayList<>(agents);
        computeDefaultActions();
        return this;
    }

    public Track withAgentActions(Map<String, String> actions) {
        this.agentActions = new HashMap<>(actions);
        return this;
    }

    public Track withColor(String color) {
        this.color = color;
        return this;
    }

    public Track withLabel(String label) {
        this.label = label;
        return this;
    }

    // ── Computed properties ────────────────────────────────────

    /** Agents that will generate output this round. */
    public List<String> getActiveAgents() {
        return agents.stream()
                .filter(n -> "active".equals(agentActions.get(n)))
                .collect(Collectors.toList());
    }

    /** Agents that listen but don't speak. */
    public List<String> getSilentAgents() {
        return agents.stream()
                .filter(n -> "silent".equals(agentActions.get(n)))
                .collect(Collectors.toList());
    }

    /** Is this a group track （>2 agents in merged mode）. */
    public boolean isGroup() {
        return agents.size() > 2 && mode == Mode.MERGED;
    }

    // ── Default action computation ─────────────────────────────

    private void computeDefaultActions() {
        if (!agentActions.isEmpty()) return;
        switch (mode) {
            case WEAK -> {
                for (int i = 0; i < agents.size(); i++) {
                    agentActions.put(agents.get(i), i == 0 ? "active" : "silent");
                }
            }
            case ISOLATED -> agents.forEach(n -> agentActions.put(n, "offline"));
            default -> agents.forEach(n -> agentActions.put(n, "active"));
        }
    }

    // ── Serialization ──────────────────────────────────────────

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("agents", agents);
        m.put("agent_actions", agentActions);
        m.put("mode", mode.name().toLowerCase());
        m.put("color", color);
        m.put("label", label);
        return m;
    }

    public static Track fromMap(Map<String, Object> data) {
        Track t = new Track();
        t.id = (String) data.getOrDefault("id", "main");
        t.mode = Mode.valueOf(((String) data.getOrDefault("mode", "merged")).toUpperCase());
        t.color = (String) data.getOrDefault("color", "#4CAF50");
        t.label = (String) data.getOrDefault("label", "主线");

        @SuppressWarnings("unchecked")
        List<String> agentList = (List<String>) data.getOrDefault("agents", List.of());
        t.agents = new ArrayList<>(agentList);

        @SuppressWarnings("unchecked")
        Map<String, String> actions = (Map<String, String>) data.getOrDefault("agent_actions", Map.of());
        t.agentActions = new HashMap<>(actions);

        if (t.agentActions.isEmpty()) {
            t.computeDefaultActions();
        }
        return t;
    }

    // ── Plain Getters/Setters ──────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getAgents() { return agents; }
    public void setAgents(List<String> agents) { this.agents = agents; }
    public Map<String, String> getAgentActions() { return agentActions; }
    public void setAgentActions(Map<String, String> agentActions) { this.agentActions = agentActions; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    @Override
    public String toString() {
        return "Track{" + id + " [" + mode + "] " + agents + "}";
    }
}
