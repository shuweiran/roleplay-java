package com.roleplay.engine.service;

import com.roleplay.engine.llm.LLMClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI-powered generation for characters and scenes.
 * Maps from Python Arbiter.generate_scene() / generate_character() methods.
 */
@Service
public class GeneratorService {

    private final LLMClient llmClient;

    public GeneratorService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /** Generate a scene description via LLM. */
    public Map<String, String> generateScene(String keywords, String currentScene) {
        String context = "";
        if (currentScene != null && !currentScene.isEmpty()) {
            context = "当前场景：" + currentScene + "\n\n请基于此生成一个相关但不同的场景。";
        }
        String kw = (keywords != null && !keywords.isEmpty())
            ? (keywords.length() > 100 ? keywords.substring(0, 100) : keywords)
            : "生成一个适合多角色互动的场景";

        String prompt = String.format("""
            你是一个角色扮演游戏的主控（DM）。请生成一个场景设定。

            %s
            用户提示：%s

            请返回JSON格式：{"name": "场景名称", "description": "场景描述（80-120字）"}
            只返回JSON，不要其他文字。
            """, context, kw);

        Map<String, Object> result = llmClient.callJson(prompt, 300);
        Map<String, String> output = new LinkedHashMap<>();
        output.put("name", (String) result.getOrDefault("name", "新场景"));
        output.put("description", (String) result.getOrDefault("description", "一个普通的场景。"));
        return output;
    }

    /** Generate a character via LLM. */
    public Map<String, String> generateCharacter(String keywords) {
        String kw = (keywords != null && !keywords.isEmpty())
            ? (keywords.length() > 100 ? keywords.substring(0, 100) : keywords)
            : "Generate an interesting character";

        String prompt = String.format("""
            You are a roleplay DM. Generate a character.
            User prompt: %s
            Return JSON only: {"name": "name", "persona": "personality 60-100 chars",
            "voice": "speaking style 30-60 chars", "background": "backstory 50-80 chars"}
            """, kw);

        Map<String, Object> result = llmClient.callJson(prompt, 400);
        Map<String, String> output = new LinkedHashMap<>();
        output.put("name", (String) result.getOrDefault("name", "路人"));
        output.put("persona", (String) result.getOrDefault("persona", "普通角色"));
        output.put("voice", (String) result.getOrDefault("voice", "正常"));
        output.put("background", (String) result.getOrDefault("background", "未知"));
        return output;
    }
}
