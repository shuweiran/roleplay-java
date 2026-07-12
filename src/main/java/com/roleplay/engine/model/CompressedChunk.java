package com.roleplay.engine.model;

import java.util.*;

/**
 * Compressed conversation chunk — periodic summary of dialogue history.
 * Maps from Python models/domain.py → CompressedChunk.
 */
public class CompressedChunk {
    private String chunkId;
    private int startRound;
    private int endRound;
    private String summary = "";
    private List<String> keyEvents = new ArrayList<>();
    private List<String> openLoops = new ArrayList<>();
    private double importance = 0.5;

    public CompressedChunk() {}

    public CompressedChunk(String chunkId, int startRound, int endRound,
                           String summary, List<String> keyEvents,
                           List<String> openLoops, double importance) {
        this.chunkId = chunkId;
        this.startRound = startRound;
        this.endRound = endRound;
        this.summary = summary != null ? summary : "";
        this.keyEvents = keyEvents != null ? keyEvents : new ArrayList<>();
        this.openLoops = openLoops != null ? openLoops : new ArrayList<>();
        this.importance = importance;
    }

    /** Human-readable context string matching Python's context_string property. */
    public String getContextString() {
        List<String> parts = new ArrayList<>();
        parts.add("[" + startRound + "-" + endRound + "轮] " + summary);
        if (!keyEvents.isEmpty()) {
            parts.add("事件: " + String.join("、", keyEvents.subList(0, Math.min(3, keyEvents.size()))));
        }
        if (!openLoops.isEmpty()) {
            parts.add("线索: " + String.join("、", openLoops.subList(0, Math.min(2, openLoops.size()))));
        }
        return String.join(" | ", parts);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chunk_id", chunkId);
        m.put("start_round", startRound);
        m.put("end_round", endRound);
        m.put("summary", summary);
        m.put("key_events", keyEvents);
        m.put("open_loops", openLoops);
        m.put("importance", importance);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static CompressedChunk fromMap(Map<String, Object> data) {
        return new CompressedChunk(
            (String) data.getOrDefault("chunk_id", ""),
            ((Number) data.getOrDefault("start_round", 0)).intValue(),
            ((Number) data.getOrDefault("end_round", 0)).intValue(),
            (String) data.getOrDefault("summary", ""),
            (List<String>) data.getOrDefault("key_events", List.of()),
            (List<String>) data.getOrDefault("open_loops", List.of()),
            ((Number) data.getOrDefault("importance", 0.5)).doubleValue()
        );
    }

    // ── Getters/Setters ──
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public int getStartRound() { return startRound; }
    public int getEndRound() { return endRound; }
    public String getSummary() { return summary; }
    public List<String> getKeyEvents() { return keyEvents; }
    public List<String> getOpenLoops() { return openLoops; }
    public double getImportance() { return importance; }
}
