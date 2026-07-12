package com.roleplay.engine.service;

import com.roleplay.engine.agent.Agent;
import com.roleplay.engine.agent.AgentExecutor;
import com.roleplay.engine.core.Message;
import com.roleplay.engine.core.Persona;
import com.roleplay.engine.core.Track;
import com.roleplay.engine.core.TrackConfig;
import com.roleplay.engine.llm.LLMClient;
import com.roleplay.engine.model.CompressedChunk;
import com.roleplay.engine.model.Session;
import com.roleplay.engine.service.ArbiterService.TrackConfigResult;
import com.roleplay.engine.service.ArbiterService.UserInputCategory;
import com.roleplay.engine.service.TrackRequestService.TrackChangeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ⭐ Round orchestrator — the heart of the roleplay system.
 *
 * <p>Coordinates Arbiter (track config) → AgentExecutor (parallel agent execution)
 * → Arbiter (output integration) → MemoryStore (persistence).
 *
 * <p>Maps from Python core/router.py (2200+ lines, the biggest mess).
 * In Java this is CLEAN — no mixin, no dead code, no serial agent execution.
 */
@Service
public class RouterService {
    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private final ArbiterService arbiter;
    private final AgentExecutor executor;
    private final MemoryStore memory;
    private final Compressor compressor;
    private final Monitor monitor;
    private final GeneratorService generator;
    private final TrackRequestService trackRequestService;
    private final LLMClient llmClient;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, Object> state = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private String mode = "free";        // free | protagonist | multi_track | director | werewolf
    private String protagonist = "";
    private String directorCharacter = "";
    private List<String> goals = new ArrayList<>();
    private Set<String> restrictedAgents = new HashSet<>();
    private String sceneDescription = "";
    private int roundCount = 0;
    private List<Map<String, Object>> previousTracks = new ArrayList<>();
    private String sessionId = "";

    public RouterService(ArbiterService arbiter, AgentExecutor executor,
                         MemoryStore memory, Compressor compressor,
                         Monitor monitor, GeneratorService generator,
                         TrackRequestService trackRequestService,
                         LLMClient llmClient) {
        this.arbiter = arbiter;
        this.executor = executor;
        this.memory = memory;
        this.compressor = compressor;
        this.monitor = monitor;
        this.generator = generator;
        this.trackRequestService = trackRequestService;
        this.llmClient = llmClient;
        memory.setCompressor(compressor);
    }

    // ═══════════════════════════════════════════════════════════
    //  Session lifecycle
    // ═══════════════════════════════════════════════════════════

    public void initSession(String sessionId, List<Persona> personas,
                             String sceneDescription, String mode,
                             String protagonist, String directorCharacter) {
        this.sessionId = sessionId;
        this.sceneDescription = sceneDescription;
        this.mode = mode;
        this.protagonist = protagonist != null ? protagonist : "";
        this.directorCharacter = directorCharacter != null ? directorCharacter : "";
        this.roundCount = 0;
        this.previousTracks = new ArrayList<>();
        this.goals = new ArrayList<>();
        this.restrictedAgents = new HashSet<>();
        this.running = true;

        agents.clear();
        List<String> agentNames = new ArrayList<>();
        for (Persona p : personas) {
            Agent agent = new Agent(p, "agent", llmClient);
            agents.put(p.getName(), agent);
            agentNames.add(p.getName());
        }

        // Create memory session
        memory.createSession(sessionId, agentNames, Map.of(
            "mode", mode, "scene", sceneDescription));
        state.put("status", "initialized");
        log.info("Session {} initialized with {} agents, mode={}", sessionId, agentNames.size(), mode);
    }

    public void loadSession(Session session, List<Agent> agentList) {
        this.sessionId = session.getSessionId();
        this.sceneDescription = session.getCurrentScene();
        this.roundCount = session.getRoundCount();
        memory.setSession(session);
        agents.clear();
        for (Agent a : agentList) {
            agents.put(a.getName(), a);
        }
        running = true;
        log.info("Loaded session {}", sessionId);
    }

    public Map<String, Object> getState() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("status", running ? "running" : "idle");
        s.put("mode", mode);
        s.put("session_id", sessionId);
        s.put("round", roundCount);
        s.put("scene", sceneDescription);
        s.put("agent_count", agents.size());
        s.put("agent_names", agents.keySet().stream().toList());
        s.put("protagonist", protagonist);
        s.put("director_character", directorCharacter);
        s.put("goals", goals);
        s.put("message_count", memory.hasSession() ? memory.getSession().getMessages().size() : 0);
        if (monitor != null) s.put("cost_report", monitor.getCostReport());
        return s;
    }

    public boolean isRunning() { return running; }
    public void stop() { running = false; }

    // ═══════════════════════════════════════════════════════════
    //  Core round execution
    // ═══════════════════════════════════════════════════════════

    /**
     * Execute one full conversation round:
     * 1. Arbiter configures tracks
     * 2. AgentExecutor runs all agents in parallel (Virtual Threads!)
     * 3. Arbiter integrates outputs
     * 4. Memory saves, compressor checks
     */
    public RoundResult runRound(String userInput, String userInterjection) {
        if (!running || agents.isEmpty()) {
            return RoundResult.error("No active session");
        }

        roundCount++;
        List<String> agentNames = new ArrayList<>(agents.keySet());
        String historySummary = memory.getSummaryContext();

        // Step 1: Silent process pending track requests
        trackRequestService.silentProcessPending(sessionId, goals);

        // Step 2: Configure tracks via Arbiter
        TrackConfigResult trackResult = arbiter.configureTracks(
            sceneDescription, agentNames, historySummary,
            mode, protagonist, previousTracks, goals, restrictedAgents);
        previousTracks = trackResult.tracks;

        // Build TrackConfig for executor
        TrackConfig config = buildTrackConfig(trackResult.tracks, roundCount);

        // Step 2: Handle user input (convert to narration)
        String narration = null;
        if (userInput != null && !userInput.isBlank()) {
            if (userInput.startsWith("/")) {
                // Handle commands
                narration = handleCommand(userInput);
            } else {
                UserInputCategory cat = arbiter.classifyUserInput(
                    userInput, "always", memory.getShortTermContextRaw(2));
                narration = arbiter.processUserInput(
                    userInput, cat, sceneDescription, agentNames, goals);
            }
            if (narration != null) {
                Message userMsg = new Message(Message.Role.USER, "me", narration);
                userMsg.setRoundNumber(roundCount);
                memory.addMessage(userMsg);
            }
        }

        // Step 3: Execute all agents in parallel
        Map<String, Agent> agentMap = new HashMap<>(agents);
        AgentExecutor.ContextBuilder ctxBuilder = (agentName, trackMode, trackId, cfg) ->
            buildAgentContext(agentName, trackMode, trackId);

        AgentExecutor.ExecutionResult execResult = executor.executeRound(config, agentMap, ctxBuilder);

        // Step 4: Collect agent outputs
        List<Map<String, Object>> agentOutputs = new ArrayList<>();
        for (AgentExecutor.AgentOutput output : execResult.outputs()) {
            if (output.isSuccess() && output.content() != null && !output.content().isBlank()) {
                Message agentMsg = new Message(Message.Role.AGENT, output.agentName(), output.content());
                agentMsg.setRoundNumber(roundCount);
                agentMsg.setTrackId(output.trackId());
                memory.addMessage(agentMsg);

                Map<String, Object> outMap = new LinkedHashMap<>();
                outMap.put("agent_name", output.agentName());
                outMap.put("content", output.content());
                outMap.put("track_id", output.trackId());
                agentOutputs.add(outMap);
            }
        }

        // Step 5: Integrate outputs via Arbiter
        Map<String, Object> integration = arbiter.integrateOutputs(
            sceneDescription, trackResult.tracks, agentOutputs, "werewolf".equals(mode));

        String narrationText = (String) integration.getOrDefault("narration", "");
        if (narrationText != null && !narrationText.isBlank()) {
            Message arbiterMsg = new Message(Message.Role.ARBITER, "主控", narrationText);
            arbiterMsg.setRoundNumber(roundCount);
            memory.addMessage(arbiterMsg);
        }

        // Step 6: Check compression
        if (compressor.shouldCompress(roundCount)) {
            List<Map<String, String>> recentRaw = memory.getShortTermContextRaw(compressor.getCompressionInterval());
            CompressedChunk chunk = compressor.compress(recentRaw,
                Math.max(0, roundCount - compressor.getCompressionInterval()), roundCount);
            if (memory.hasSession()) {
                memory.getSession().getCompressedChunks().add(chunk);
            }
        }

        memory.incrementRound();
        String status = execResult.metrics() != null
            ? String.format("%d agents in %.0fms", agentOutputs.size(), execResult.metrics().totalRoundTimeMs())
            : agentOutputs.size() + " agents done";

        return new RoundResult(status, agentOutputs, integration, trackResult.reasoning,
            execResult.metrics() != null ? execResult.metrics().toMap() : Map.of());
    }

    /** Run multiple automatic rounds. */
    public List<RoundResult> runAutoRounds(int count) {
        List<RoundResult> results = new ArrayList<>();
        for (int i = 0; i < count && running; i++) {
            results.add(runRound(null, null));
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  Agent context building
    // ═══════════════════════════════════════════════════════════

    private String buildAgentContext(String agentName, String trackMode, String trackId) {
        Agent agent = agents.get(agentName);
        if (agent == null) return "";

        List<String> contextParts = new ArrayList<>();

        // Scene context
        if (sceneDescription != null && !sceneDescription.isEmpty()) {
            contextParts.add("【当前场景】\n" + sceneDescription);
        }

        // Track info
        contextParts.add("【轨道】\n" + trackId + " (" + trackMode + "模式)");

        // Summary context
        String summary = memory.getSummaryContext();
        if (!summary.isEmpty()) contextParts.add(summary);

        // Recent messages visible to this agent
        List<Message> visible = memory.getAgentContext(agentName, 30);
        if (!visible.isEmpty()) {
            StringBuilder history = new StringBuilder("【对话历史】\n");
            for (Message m : visible) {
                history.append("[").append(m.getName()).append("]: ")
                       .append(m.getContent()).append("\n");
            }
            contextParts.add(history.toString());
        }

        return String.join("\n\n", contextParts);
    }

    // ═══════════════════════════════════════════════════════════
    //  Commands
    // ═══════════════════════════════════════════════════════════

    private String handleCommand(String cmd) {
        String stripped = cmd.strip().toLowerCase();
        if (stripped.startsWith("/mode")) {
            String[] parts = cmd.split("\\s+", 2);
            if (parts.length > 1) {
                mode = parts[1].trim();
                return "【系统】模式切换为: " + mode;
            }
            return "【系统】当前模式: " + mode;
        }
        if (stripped.startsWith("/goal") || stripped.startsWith("/goals")) {
            String[] parts = cmd.split("\\s+", 2);
            if (parts.length > 1) {
                goals = Arrays.asList(parts[1].trim().split("[,，]"));
                return "【系统】剧情目标已更新: " + String.join(", ", goals);
            }
            return "【系统】当前目标: " + (goals.isEmpty() ? "无" : String.join(", ", goals));
        }
        if (stripped.startsWith("/protagonist")) {
            String[] parts = cmd.split("\\s+", 2);
            if (parts.length > 1) {
                protagonist = parts[1].trim();
                mode = "protagonist";
                return "【系统】主角模式，主角: " + protagonist;
            }
            return "【系统】当前主角: " + (protagonist.isEmpty() ? "未设置" : protagonist);
        }
        if (stripped.startsWith("/restrict")) {
            restrictedAgents.clear();
            String[] parts = cmd.split("\\s+", 2);
            if (parts.length > 1) {
                restrictedAgents.addAll(Arrays.asList(parts[1].trim().split("[,，]")));
                return "【系统】禁止出场: " + String.join(", ", restrictedAgents);
            }
            return "【系统】禁止列表已清空";
        }
        if (stripped.equals("/stop") || stripped.equals("/end")) {
            running = false;
            return "【系统】对话已停止";
        }
        if (stripped.equals("/status")) {
            return "【系统状态】模式=" + mode + " 轮次=" + roundCount
                + " Agent数=" + agents.size() + " 运行中=" + running;
        }
        return "【系统】未知命令: " + cmd;
    }

    // ═══════════════════════════════════════════════════════════
    //  Config mutations
    // ═══════════════════════════════════════════════════════════

    public void setMode(String mode) { this.mode = mode; }
    public String getMode() { return mode; }
    public void setProtagonist(String name) { this.protagonist = name; }
    public void setDirectorCharacter(String name) { this.directorCharacter = name; }
    public void setGoals(List<String> goals) { this.goals = goals; }
    public List<String> getGoals() { return goals; }
    public int getRoundCount() { return roundCount; }
    public void setSceneDescription(String desc) { this.sceneDescription = desc; }

    public void addAgent(String name, Persona persona) {
        agents.put(name, new Agent(persona, "agent", llmClient));
    }

    public void removeAgent(String name) {
        agents.remove(name);
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private TrackConfig buildTrackConfig(List<Map<String, Object>> trackMaps, int round) {
        TrackConfig config = new TrackConfig(round);
        for (Map<String, Object> m : trackMaps) {
            Track track = Track.fromMap(m);
            config.addTrack(track);
        }
        return config;
    }

    // ═══════════════════════════════════════════════════════════
    //  Value objects
    // ═══════════════════════════════════════════════════════════

    public static class RoundResult {
        public final String status;
        public final List<Map<String, Object>> agentOutputs;
        public final Map<String, Object> integration;
        public final String reasoning;
        public final Map<String, Object> metrics;

        public RoundResult(String status, List<Map<String, Object>> agentOutputs,
                           Map<String, Object> integration, String reasoning,
                           Map<String, Object> metrics) {
            this.status = status;
            this.agentOutputs = agentOutputs;
            this.integration = integration;
            this.reasoning = reasoning;
            this.metrics = metrics;
        }

        public static RoundResult error(String msg) {
            return new RoundResult("error: " + msg, List.of(), Map.of(), "", Map.of());
        }
    }
}
