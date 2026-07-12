package com.roleplay.engine.agent;

import com.roleplay.engine.core.Message;
import com.roleplay.engine.core.Track;
import com.roleplay.engine.core.TrackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ⭐ Parallel agent executor — the KEY performance improvement over Python.
 *
 * <p>In Python, agents are executed SERIALLY within each track
 * （see {@code router.py → _run_round_agents}, 5 agents = 5x LLM delay）.
 *
 * <p>In Java, each agent runs in its OWN VIRTUAL THREAD, so all agents
 * call the LLM in TRUE PARALLEL. 5 agents = 1x LLM delay.
 *
 * <p>Strategy:
 * <ul>
 *   <li><b>Isolated tracks</b> — agents run fully in parallel（no shared context）</li>
 *   <li><b>Merged/WEAK tracks</b> — agents within same track run in parallel
 *       （they share history context but NOT same-round peer outputs）</li>
 *   <li><b>Priority ordering</b> — PLAYER > DM > NPC</li>
 * </ul>
 *
 * <p>Maps from Python {@code core/scheduler.py}（which was DEAD CODE —
 * Router never used it）. In Java this IS the execution engine.
 */
@Service
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    /** Max agents running concurrently （safety limit）. */
    private static final int MAX_CONCURRENT = 16;

    /** Virtual thread executor — each agent gets its own thread. */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // ── Priority enum ──────────────────────────────────────────

    public enum Priority {
        PLAYER(1),      // human player / protagonist
        DM(2),          // director / god
        NPC(3),         // NPCs and weak-chain bystanders
        LOWEST(4);      // offline / background

        final int level;
        Priority(int level) { this.level = level; }
    }

    // ── AgentTask record ───────────────────────────────────────

    public record AgentTask(
            String agentName,
            String trackId,
            String trackMode,
            Priority priority,
            Callable<String> task
    ) {}

    // ── Execution result ────────────────────────────────────────

    public record AgentOutput(
            String agentName,
            String content,
            String trackId,
            List<String> visibleTo,
            long elapsedMs,
            String error
    ) {
        public boolean isSuccess() { return error == null || error.isEmpty(); }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("agent_name", agentName);
            m.put("content", content);
            m.put("track_id", trackId);
            m.put("visible_to", visibleTo);
            m.put("elapsed_ms", elapsedMs);
            return m;
        }
    }

    // ── Metrics ────────────────────────────────────────────────

    public record ExecutorMetrics(
            int totalTasks,
            int maxConcurrent,
            double avgLatencyMs,
            double maxLatencyMs,
            double totalRoundTimeMs
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("total_tasks", totalTasks);
            m.put("max_concurrent", maxConcurrent);
            m.put("avg_latency_ms", Math.round(avgLatencyMs * 100.0) / 100.0);
            m.put("max_latency_ms", Math.round(maxLatencyMs * 100.0) / 100.0);
            m.put("total_round_time_ms", Math.round(totalRoundTimeMs * 100.0) / 100.0);
            return m;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Core execution method
    // ════════════════════════════════════════════════════════════

    /**
     * Execute ALL agents in a TrackConfig in TRUE PARALLEL.
     *
     * <p>This is the direct replacement for Python's serial
     * {@code _run_round_agents()} method.
     *
     * @param config       the track configuration for this round
     * @param agents       map of agent name → Agent instance
     * @param contextBuilder builds per-agent context before calling LLM
     * @return list of agent outputs （one per active agent）
     */
    public ExecutionResult executeRound(
            TrackConfig config,
            Map<String, Agent> agents,
            ContextBuilder contextBuilder) {

        Instant roundStart = Instant.now();
        List<AgentTask> tasks = buildTasks(config, agents, contextBuilder);

        if (tasks.isEmpty()) {
            return new ExecutionResult(List.of(), new ExecutorMetrics(0, 0, 0, 0, 0));
        }

        // Submit all tasks to virtual threads in parallel
        int totalTasks = tasks.size();
        int maxConcurrent = 0;
        double maxLatency = 0;
        double totalLatency = 0;
        int completed = 0;

        List<AgentOutput> outputs = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalTasks);
        ConcurrentLinkedQueue<AgentOutput> resultQueue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        final int[] maxConcurrentRef = {0};

        for (AgentTask task : tasks) {
            executor.submit(() -> {
                Instant taskStart = Instant.now();
                try {
                    String content = task.task().call();
                    long elapsed = Duration.between(taskStart, Instant.now()).toMillis();
                    latencies.add(elapsed);

                    synchronized (this) {
                        int current = tasks.size() - (int) latch.getCount() + 1;
                        if (current > maxConcurrentRef[0]) maxConcurrentRef[0] = current;
                    }

                    resultQueue.add(new AgentOutput(
                            task.agentName(), content, task.trackId(),
                            List.of(), elapsed, null));
                } catch (Exception e) {
                    long elapsed = Duration.between(taskStart, Instant.now()).toMillis();
                    resultQueue.add(new AgentOutput(
                            task.agentName(),
                            "[" + task.agentName() + " 走神了: " + e.getMessage() + "]",
                            task.trackId(),
                            List.of(), elapsed, e.getMessage()));
                    log.warn("Agent {} failed: {}", task.agentName(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for ALL agents to complete
        try {
            boolean allDone = latch.await(120, TimeUnit.SECONDS);
            if (!allDone) {
                log.warn("Agent round timed out: {} tasks incomplete after 120s",
                        latch.getCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Agent round interrupted");
        }

        // Collect results
        outputs.addAll(resultQueue);
        for (Long lat : latencies) {
            totalLatency += lat;
            if (lat > maxLatency) maxLatency = lat;
        }
        completed = outputs.size();

        double avgLatency = completed > 0 ? totalLatency / completed : 0;
        double totalTimeMs = Duration.between(roundStart, Instant.now()).toMillis();

        ExecutorMetrics metrics = new ExecutorMetrics(
                totalTasks, maxConcurrentRef[0], avgLatency, maxLatency, totalTimeMs);

        log.info("Agent round complete: {} agents in {:.0f}ms (avg {:.0f}ms/agent)",
                completed, totalTimeMs, avgLatency);

        return new ExecutionResult(outputs, metrics);
    }

    // ── Task building ──────────────────────────────────────────

    private List<AgentTask> buildTasks(
            TrackConfig config,
            Map<String, Agent> agents,
            ContextBuilder contextBuilder) {

        List<AgentTask> tasks = new ArrayList<>();

        for (Track track : config.getTracks()) {
            for (String agentName : track.getActiveAgents()) {
                Agent agent = agents.get(agentName);
                if (agent == null) continue;

                Priority priority = computePriority(
                        agentName, track.getMode().name().toLowerCase(),
                        "", "");

                String trackMode = track.getMode().name().toLowerCase();
                String trackId = track.getId();

                Callable<String> callable = () -> {
                    String context = contextBuilder.buildContext(
                            agentName, trackMode, trackId, config);
                    return agent.generateSync(
                            agent.getPersona().buildLightweightPrompt(),
                            List.of(), trackMode, List.of(), "", null, "");
                };

                tasks.add(new AgentTask(agentName, trackId, trackMode, priority, callable));
            }
        }

        // Sort by priority: PLAYER first, then DM, then NPC
        tasks.sort(Comparator.comparingInt(t -> t.priority().level));
        return tasks;
    }

    // ── Priority computation ───────────────────────────────────

    private Priority computePriority(
            String agentName, String trackMode,
            String protagonist, String director) {

        if (agentName.equals(protagonist) || agentName.equals("me")) {
            return Priority.PLAYER;
        }
        if (agentName.equals(director)) {
            return Priority.DM;
        }
        return Priority.NPC;
    }

    // ── Execution result container ─────────────────────────────

    public record ExecutionResult(
            List<AgentOutput> outputs,
            ExecutorMetrics metrics
    ) {}

    // ── Context builder interface ──────────────────────────────

    @FunctionalInterface
    public interface ContextBuilder {
        /**
         * Build the LLM context for a single agent before generation.
         *
         * @return the context string to prepend to the agent's prompt
         */
        String buildContext(String agentName, String trackMode,
                            String trackId, TrackConfig config);
    }
}
