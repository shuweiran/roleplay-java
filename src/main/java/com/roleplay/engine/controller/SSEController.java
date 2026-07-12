package com.roleplay.engine.controller;

import com.roleplay.engine.service.RouterService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;

/**
 * SSE (Server-Sent Events) streaming endpoint.
 * Maps from Python api/routes_sse.py.
 *
 * <p>Uses Reactor's Sinks.Many for backpressure-aware event broadcasting.
 */
@RestController
@RequestMapping("/api/events")
public class SSEController {

    private final Sinks.Many<String> eventSink = Sinks.many().multicast().onBackpressureBuffer(256);
    private final RouterService router;

    public SSEController(RouterService router) {
        this.router = router;
    }

    /**
     * SSE event stream — the frontend's long-lived connection for real-time updates.
     * Clients receive agent outputs, round completions, system messages, etc.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return Flux.merge(
            // Heartbeat every 15s to keep connection alive
            Flux.interval(Duration.ofSeconds(15)).map(i -> ": heartbeat\n\n"),
            // Main event stream
            eventSink.asFlux().map(data -> "data: " + data + "\n\n")
        );
    }

    /**
     * Broadcast an event to all connected SSE clients.
     */
    public void broadcast(String eventType, String data) {
        String payload = "event: " + eventType + "\ndata: " + data + "\n\n";
        Sinks.EmitResult result = eventSink.tryEmitNext(payload);
        if (result.isFailure()) {
            // Drop events when backpressure threshold exceeded
            Sinks.EmitResult retry = eventSink.tryEmitNext(payload);
        }
    }

    public void broadcastAgentOutput(String agentName, String content) {
        broadcast("agent_output", "{\"agent_name\":\"" + agentName
            + "\",\"content\":\"" + content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}");
    }

    public void broadcastRoundComplete(String status) {
        broadcast("round_complete", "{\"status\":\"" + status + "\"}");
    }

    public void broadcastError(String message) {
        broadcast("error", "{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
