package com.roleplay.engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * TTS service — Edge TTS and CosyVoice streaming.
 * Maps from Python services/tts_service.py.
 *
 * <p>Edge TTS: free, no API key, multi-language streaming.
 * CosyVoice: requires DashScope API key, higher quality.
 */
@Service
public class TtsService {
    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    public enum Engine { EDGE, COSYVOICE }

    private Engine currentEngine = Engine.EDGE;
    private boolean enabled = true;

    public void setEngine(Engine engine) { this.currentEngine = engine; }
    public Engine getEngine() { return currentEngine; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Stream TTS audio for the given text.
     * In production this would call Edge TTS CLI or CosyVoice API.
     * Returns a placeholder URL for now.
     */
    public String synthesize(String text, String voice, Engine engine) {
        if (!enabled || text == null || text.isBlank()) return "";
        log.debug("TTS: engine={}, voice={}, text={}chars", engine, voice, text.length());
        // TODO: Actual TTS integration
        // Edge TTS: edge-tts --voice zh-CN-Xiaoxiao --text "..." --write-media output.mp3
        // CosyVoice: REST API call to dashscope with voice_id
        return "/api/voice/audio/placeholder_" + System.currentTimeMillis() + ".mp3";
    }

    public String synthesize(String text, String voice) {
        return synthesize(text, voice, currentEngine);
    }

    /**
     * Pick the best engine based on scene complexity.
     * Single character → Edge (low latency). Multi-character → CosyVoice (quality).
     */
    public Engine selectEngine(int agentCount, boolean isNarration) {
        if (agentCount <= 2 && !isNarration) return Engine.EDGE;
        return Engine.COSYVOICE;
    }
}
