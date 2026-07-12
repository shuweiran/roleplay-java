package com.roleplay.engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Whisper speech-to-text service.
 * Maps from Python services/whisper_service.py.
 */
@Service
public class WhisperService {
    private static final Logger log = LoggerFactory.getLogger(WhisperService.class);

    public enum Provider { LOCAL, GROQ, OPENAI }

    private Provider provider = Provider.LOCAL;
    private boolean enabled = false;

    public void setProvider(Provider p) { this.provider = p; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    /**
     * Transcribe audio bytes to text.
     * In production: LOCAL → faster-whisper, GROQ → Groq API, OPENAI → Whisper API.
     */
    public String transcribe(byte[] audioData, String format) {
        if (!enabled || audioData == null || audioData.length == 0) return "";
        log.debug("Whisper: provider={}, format={}, size={}B", provider, format, audioData.length);
        // TODO: Actual transcription integration
        return "(语音识别待集成)";
    }
}
