package com.roleplay.engine.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lorebook — keyword-triggered world knowledge injection.
 * When conversation mentions a keyword, injects the associated lore context.
 * Maps from Python core/lorebook.py (~280 lines).
 */
@Service
public class LorebookService {

    public static class LoreEntry {
        final String id;
        final String keyword;
        final String content;
        final int priority;       // higher = inject first
        final int maxInjects;     // max times this entry can fire per session (-1 = unlimited)
        int injectCount = 0;

        public LoreEntry(String id, String keyword, String content, int priority, int maxInjects) {
            this.id = id;
            this.keyword = keyword;
            this.content = content;
            this.priority = priority;
            this.maxInjects = maxInjects;
        }

        public boolean canInject() {
            return maxInjects < 0 || injectCount < maxInjects;
        }
    }

    private final Map<String, List<LoreEntry>> sessionLore = new ConcurrentHashMap<>();
    private final List<LoreEntry> globalLore = new ArrayList<>();

    // Cache compiled patterns
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public LorebookService() {
        // Default global lore entries
        addGlobalEntry("魔法", "这个世界中，魔法分为七大元素派系。普通人在满月之夜也可能获得短暂的魔法感知。", 1, -1);
        addGlobalEntry("古堡", "这座古堡建于三百年前，地下有错综复杂的秘道网络。据说城堡的建造者是一位痴迷炼金术的伯爵。", 2, -1);
        addGlobalEntry("龙", "龙是这个世界的上古生物，分为元素龙和骸骨龙两大分支。最后一条元素龙在五十年前消失。", 1, -1);
        addGlobalEntry("森林", "迷雾森林是大陆上最古老的原始森林，林中常有幻象和时空错乱的现象。深处有一座废弃的精灵神庙。", 1, -1);
        addGlobalEntry("王城", "王城是帝国的心脏，分为上城（贵族区）、中城（商区）和下城（平民区）。皇城地下有古代遗迹。", 1, -1);
    }

    // ═══════════════════════════════════════════════════════════
    //  Entry management
    // ═══════════════════════════════════════════════════════════

    public LoreEntry addGlobalEntry(String keyword, String content, int priority, int maxInjects) {
        LoreEntry entry = new LoreEntry("g_" + keyword, keyword, content, priority, maxInjects);
        globalLore.add(entry);
        patternCache.remove(keyword);
        return entry;
    }

    public LoreEntry addSessionEntry(String sessionId, String keyword, String content, int priority, int maxInjects) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        LoreEntry entry = new LoreEntry(id, keyword, content, priority, maxInjects);
        sessionLore.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(entry);
        patternCache.remove(sessionId + "_" + keyword);
        return entry;
    }

    public void removeEntry(String sessionId, String entryId) {
        if (sessionId.isEmpty()) {
            globalLore.removeIf(e -> e.id.equals(entryId));
        } else {
            sessionLore.getOrDefault(sessionId, List.of()).removeIf(e -> e.id.equals(entryId));
        }
    }

    public List<LoreEntry> getAllEntries(String sessionId) {
        List<LoreEntry> all = new ArrayList<>(globalLore);
        all.addAll(sessionLore.getOrDefault(sessionId, List.of()));
        return all;
    }

    // ═══════════════════════════════════════════════════════════
    //  Keyword matching & injection
    // ═══════════════════════════════════════════════════════════

    /**
     * Scan text for lore keywords and return matching entries sorted by priority.
     * Used by RouterService to inject relevant lore into agent context.
     */
    public List<LoreEntry> scanText(String sessionId, String text) {
        if (text == null || text.isEmpty()) return List.of();

        Set<LoreEntry> matched = new LinkedHashSet<>();

        // Scan global lore
        for (LoreEntry entry : globalLore) {
            if (!entry.canInject()) continue;
            if (text.contains(entry.keyword) || text.contains(entry.keyword.toLowerCase())) {
                matched.add(entry);
            }
        }

        // Scan session-specific lore
        for (LoreEntry entry : sessionLore.getOrDefault(sessionId, List.of())) {
            if (!entry.canInject()) continue;
            if (text.contains(entry.keyword)) {
                matched.add(entry);
            }
        }

        // Sort by priority (descending)
        return matched.stream()
            .sorted((a, b) -> Integer.compare(b.priority, a.priority))
            .limit(5) // cap at 5 lore entries per scan
            .collect(Collectors.toList());
    }

    /**
     * Build lore context string from matched entries.
     * Injects into agent's system prompt.
     */
    public String buildLoreContext(String sessionId, String text) {
        List<LoreEntry> entries = scanText(sessionId, text);
        if (entries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n\n【世界观知识】\n");
        for (LoreEntry entry : entries) {
            sb.append("• ").append(entry.content).append("\n");
            entry.injectCount++;
        }
        return sb.toString();
    }

    /** Reset inject counters for a session. */
    public void resetSession(String sessionId) {
        sessionLore.remove(sessionId);
        globalLore.forEach(e -> e.injectCount = 0);
    }

    public Map<String, Object> toMap(String sessionId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("global_entries", globalLore.stream().map(e -> Map.of(
            "id", e.id, "keyword", e.keyword,
            "content", e.content.substring(0, Math.min(40, e.content.length())) + "...",
            "priority", e.priority, "inject_count", e.injectCount
        )).collect(Collectors.toList()));
        m.put("session_entries", sessionLore.getOrDefault(sessionId, List.of()).stream().map(e -> Map.of(
            "id", e.id, "keyword", e.keyword,
            "content", e.content.substring(0, Math.min(40, e.content.length())) + "...",
            "priority", e.priority, "inject_count", e.injectCount
        )).collect(Collectors.toList()));
        return m;
    }
}
