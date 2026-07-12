package com.roleplay.engine.service;

import com.roleplay.engine.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session lifecycle manager — create, save, load, prune sessions.
 * Maps from Python services/session_manager.py.
 */
@Service
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final PersistenceService persistence;

    // Auto-save config
    private static final int AUTOSAVE_INTERVAL = 5;
    private static final int MAX_MESSAGES = 500;

    public SessionManager(PersistenceService persistence) {
        this.persistence = persistence;
    }

    public Session create(String sessionId, List<String> agentNames, Map<String, Object> config) {
        Session session = new Session(sessionId, agentNames);
        if (config != null) session.getConfig().putAll(config);
        activeSessions.put(sessionId, session);
        persistence.saveSession(session);
        return session;
    }

    public Optional<Session> load(String sessionId) {
        Session cached = activeSessions.get(sessionId);
        if (cached != null) return Optional.of(cached);
        Optional<Session> loaded = persistence.loadSession(sessionId);
        loaded.ifPresent(s -> activeSessions.put(sessionId, s));
        return loaded;
    }

    public void save(Session session) {
        persistence.saveSession(session);
    }

    public void autosave(Session session) {
        if (session.getMessages().size() % AUTOSAVE_INTERVAL == 0) {
            persistence.saveSession(session);
        }
    }

    public void prune(Session session) {
        if (session.getMessages().size() > MAX_MESSAGES) {
            int excess = session.getMessages().size() - MAX_MESSAGES;
            var sublist = session.getMessages().subList(0, excess);
            sublist.clear();
            log.info("Pruned {} messages from session {}", excess, session.getSessionId());
        }
    }

    public String getLatest() {
        List<String> ids = persistence.listSessions();
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void close(String sessionId) {
        Session session = activeSessions.remove(sessionId);
        if (session != null) persistence.saveSession(session);
    }
}
