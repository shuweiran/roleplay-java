package com.roleplay.engine.model;

import java.util.*;

/**
 * Structured summary — rich conversation summary with arcs, events, and tension.
 * Maps from Python models/domain.py → StructuredSummary.
 */
public class StructuredSummary {
    private String arc = "";
    private List<String> keyEvents = new ArrayList<>();
    private List<String> openLoops = new ArrayList<>();
    private double tension = 0.5;
    private String location = "";
    private String timeProgress = "";

    public StructuredSummary() {}

    public StructuredSummary(String arc, List<String> keyEvents, List<String> openLoops,
                             double tension, String location, String timeProgress) {
        this.arc = arc != null ? arc : "";
        this.keyEvents = keyEvents != null ? keyEvents : new ArrayList<>();
        this.openLoops = openLoops != null ? openLoops : new ArrayList<>();
        this.tension = tension;
        this.location = location != null ? location : "";
        this.timeProgress = timeProgress != null ? timeProgress : "";
    }

    public String toShortString() {
        List<String> parts = new ArrayList<>();
        if (!arc.isEmpty()) parts.add("剧情线:" + arc);
        if (!openLoops.isEmpty())
            parts.add("线索:" + String.join(",", openLoops.subList(0, Math.min(2, openLoops.size()))));
        if (!location.isEmpty()) parts.add("地点:" + location);
        return parts.isEmpty() ? "" : String.join(" | ", parts);
    }

    // ── Getters/Setters ──
    public String getArc() { return arc; }
    public void setArc(String arc) { this.arc = arc; }
    public List<String> getKeyEvents() { return keyEvents; }
    public List<String> getOpenLoops() { return openLoops; }
    public double getTension() { return tension; }
    public String getLocation() { return location; }
    public String getTimeProgress() { return timeProgress; }
}
