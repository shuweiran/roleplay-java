package com.roleplay.engine.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Private chat management — whisper communication between agents.
 * Maps from Python services/private_chat.py.
 */
@Service
public class PrivateChatService {

    private final Map<String, List<Map<String, Object>>> chats = new ConcurrentHashMap<>();

    /** Request a private chat between two agents. */
    public Map<String, Object> requestChat(String from, String to, String message) {
        String key = chatKey(from, to);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("from", from);
        request.put("to", to);
        request.put("message", message);
        request.put("status", "pending");
        request.put("created_at", System.currentTimeMillis());
        chats.computeIfAbsent(key, k -> new ArrayList<>()).add(request);
        return request;
    }

    /** Reply to a pending chat request. */
    public Map<String, Object> reply(String from, String to, boolean accept) {
        String key = chatKey(from, to);
        List<Map<String, Object>> messages = chats.get(key);
        if (messages != null && !messages.isEmpty()) {
            Map<String, Object> last = messages.get(messages.size() - 1);
            last.put("status", accept ? "accepted" : "rejected");
        }
        return Map.of("status", accept ? "accepted" : "rejected");
    }

    /** Send a private message. */
    public Map<String, Object> sendMessage(String from, String to, String content) {
        String key = chatKey(from, to);
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from", from);
        msg.put("to", to);
        msg.put("content", content);
        msg.put("timestamp", System.currentTimeMillis());
        chats.computeIfAbsent(key, k -> new ArrayList<>()).add(msg);
        return msg;
    }

    /** Get chat history between two agents. */
    public List<Map<String, Object>> getHistory(String agent1, String agent2) {
        return chats.getOrDefault(chatKey(agent1, agent2), List.of());
    }

    private static String chatKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
