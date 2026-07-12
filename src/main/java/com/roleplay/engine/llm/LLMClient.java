package com.roleplay.engine.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roleplay.engine.config.AppConfig;
import com.roleplay.engine.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Unified LLM client — single HTTP connection pool for all Agent + Arbiter calls.
 *
 * <p>Consolidates retry logic, JSON parsing, and cost tracking.
 * Unlike Python's httpx + OpenAI SDK, this uses Java's built-in
 * {@link HttpClient} with Virtual Thread-friendly blocking calls.
 *
 * <p>Maps from Python {@code services/llm_client.py → LLMClient}.
 */
@Service
public class LLMClient {

    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);

    private final AppConfig appConfig;
    private final String apiBase;
    private final String model;
    private final int timeoutSeconds;
    private final String fallbackModel;

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public LLMClient(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.apiBase = appConfig.getLlm().getApiBase();
        this.model = appConfig.getLlm().getModel();
        this.timeoutSeconds = appConfig.getMonitor().getTimeoutSeconds();
        this.fallbackModel = appConfig.getMonitor().getFallbackModel();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Sync call （for Virtual Threads） ────────────────────────

    /**
     * Call the LLM with retry logic （2 models × 2 retries）.
     * This is a BLOCKING call — designed to run in a Virtual Thread.
     */
    public String callSync(List<Message> messages) {
        return callSync(messages, model, 300, 0.7);
    }

    public String callSync(List<Message> messages, String modelOverride,
                           int maxTokens, double temperature) {

        String[] modelsToTry = {modelOverride, fallbackModel};
        Set<String> seen = new LinkedHashSet<>(Arrays.asList(modelsToTry));

        Exception lastError = null;

        for (String currentModel : seen) {
            for (int retry = 0; retry < 2; retry++) {
                try {
                    String requestBody = buildChatRequest(messages, currentModel, maxTokens, temperature);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(apiBase + "/v1/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + appConfig.getLlm().getApiKey())
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        return parseResponse(response.body());
                    } else {
                        lastError = new RuntimeException(
                                "HTTP " + response.statusCode() + ": " + response.body());
                        log.warn("LLM call failed (attempt {}/2, model {}): {}",
                                retry + 1, currentModel, response.statusCode());
                    }
                } catch (Exception e) {
                    lastError = e;
                    log.warn("LLM call exception (attempt {}/2, model {}): {}",
                            retry + 1, currentModel, e.getMessage());
                }

                // Wait before retry
                if (retry == 0) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("LLM call failed after all retries: " +
                (lastError != null ? lastError.getMessage() : "unknown"));
    }

    /**
     * Async variant — returns CompletableFuture.
     */
    public CompletableFuture<String> callAsync(List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> callSync(messages));
    }

    /**
     * Simple text generation — no JSON parsing, returns raw text.
     * Uses BLOCKING call (designed for Virtual Threads).
     */
    public String callSimple(String prompt, int maxTokens) {
        Message sysMsg = new Message(Message.Role.SYSTEM, "system",
                "你是一个角色扮演主控，回复简洁的叙事旁白。");
        Message userMsg = new Message(Message.Role.USER, "user", prompt);
        try {
            return callSync(List.of(sysMsg, userMsg), model, maxTokens, 0.1);
        } catch (Exception e) {
            log.warn("callSimple failed: {}", e.getMessage());
            return null;
        }
    }

    // ── JSON mode （structured output） ──────────────────────────

    /**
     * Call LLM and parse response as JSON.
     * Includes fuzzy extraction: strips markdown fences, extracts first {…}.
     */
    public Map<String, Object> callJson(String prompt, int maxTokens) {
        Message sysMsg = new Message(Message.Role.SYSTEM, "system",
                "你是一个角色扮演主控（DM）。必须严格按照要求的JSON格式回复。");
        Message userMsg = new Message(Message.Role.USER, "user", prompt);

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String content = callSync(List.of(sysMsg, userMsg), model, maxTokens, 0.1);
                String json = extractJson(content);
                if (json != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = mapper.readValue(json, Map.class);
                    return result;
                }
            } catch (Exception e) {
                log.warn("callJson attempt {}/3 failed: {}", attempt + 1, e.getMessage());
            }
        }
        return Map.of();
    }

    // ── Internal helpers ───────────────────────────────────────

    private String buildChatRequest(List<Message> messages, String modelName,
                                    int maxTokens, double temperature)
            throws JsonProcessingException {

        List<Map<String, String>> msgList = new ArrayList<>();
        for (Message m : messages) {
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("role", switch (m.getRole()) {
                case SYSTEM -> "system";
                case USER -> "user";
                default -> "assistant";
            });
            msg.put("content", "[" + m.getName() + "] " + m.getContent());
            msgList.add(msg);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", msgList);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);

        return mapper.writeValueAsString(requestBody);
    }

    private String parseResponse(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        return root.path("choices").get(0).path("message").path("content").asText("");
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) return null;

        // Remove markdown fences
        String cleaned = text;
        if (cleaned.contains("```json")) {
            cleaned = cleaned.split("```json")[1].split("```")[0].trim();
        } else if (cleaned.contains("```")) {
            cleaned = cleaned.split("```")[1].split("```")[0].trim();
        }

        // Find first { to last }
        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return null;
    }
}
