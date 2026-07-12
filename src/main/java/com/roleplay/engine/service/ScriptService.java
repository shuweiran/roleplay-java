package com.roleplay.engine.service;

import com.roleplay.engine.llm.LLMClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Script murder mystery game — investigation and deduction.
 * Maps from Python core/script_runtime.py.
 *
 * <p>⚠️ DEVELOPMENT — basic skeleton only.
 */
@Service
public class ScriptService {

    private final LLMClient llmClient;

    public ScriptService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /** Generate a murder mystery script via LLM. */
    public Map<String, Object> generateScript(String theme, List<String> characters) {
        String prompt = String.format("""
            你是一个剧本杀创作者。请根据以下信息生成一个完整的剧本杀。
            主题：%s
            角色：%s
            请返回JSON格式：
            {"name": "剧本名称", "background": "背景故事", "roles": ["角色名"], "clues": ["线索1", "线索2"], "truth": "真相"}
            """, theme, String.join(", ", characters));

        Map<String, Object> result = llmClient.callJson(prompt, 400);
        if (result == null || result.isEmpty()) {
            result = new LinkedHashMap<>();
            result.put("name", "默认剧本");
            result.put("background", "一个普通的谋杀案");
            result.put("roles", characters);
            result.put("clues", List.of("现场有脚印", "窗户是开的"));
            result.put("truth", "凶手是管家");
        }
        return result;
    }
}
