package com.roleplay.engine.core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A round's complete track configuration.
 *
 * <p>Contains the set of tracks for one conversation round,
 * plus the arbiter's reasoning/description for this configuration.
 *
 * <p>Maps from Python {@code models/domain.py → TrackConfig}.
 */
public class TrackConfig {

    private List<Track> tracks = new ArrayList<>();
    private int round;
    private String description = "";
    private String reasoning = "";

    public TrackConfig() {}

    public TrackConfig(int round) {
        this.round = round;
    }

    // ── Computed properties ────────────────────────────────────

    /** All uniquely-named agents active across all tracks. */
    public Set<String> getActiveAgentNames() {
        return tracks.stream()
                .flatMap(t -> t.getActiveAgents().stream())
                .collect(Collectors.toSet());
    }

    /** Find which track an agent belongs to. */
    public Optional<Track> getTrackForAgent(String agentName) {
        return tracks.stream()
                .filter(t -> t.getAgents().contains(agentName))
                .findFirst();
    }

    /** Get the track mode for a specific agent. */
    public String getModeForAgent(String agentName) {
        return getTrackForAgent(agentName)
                .map(t -> t.getMode().name().toLowerCase())
                .orElse("isolated");
    }

    /** All agent names across all tracks. */
    public List<String> getAllAgentNames() {
        return tracks.stream()
                .flatMap(t -> t.getAgents().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    // ── Serialization ──────────────────────────────────────────

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("round", round);
        m.put("tracks", tracks.stream().map(Track::toMap).collect(Collectors.toList()));
        m.put("description", description);
        m.put("reasoning", reasoning);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static TrackConfig fromMap(Map<String, Object> data) {
        TrackConfig tc = new TrackConfig();
        tc.round = ((Number) data.getOrDefault("round", 0)).intValue();
        tc.description = (String) data.getOrDefault("description", "");
        tc.reasoning = (String) data.getOrDefault("reasoning", "");

        List<Map<String, Object>> trackMaps = (List<Map<String, Object>>) data.getOrDefault("tracks", List.of());
        tc.tracks = trackMaps.stream().map(Track::fromMap).collect(Collectors.toList());
        return tc;
    }

    // ── Getters/Setters ────────────────────────────────────────

    public List<Track> getTracks() { return tracks; }
    public void setTracks(List<Track> tracks) { this.tracks = tracks; }
    public void addTrack(Track track) { this.tracks.add(track); }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    @Override
    public String toString() {
        return "TrackConfig{round=" + round + ", tracks=" + tracks.size() + "}";
    }
}
