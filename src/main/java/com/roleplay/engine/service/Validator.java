package com.roleplay.engine.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Agent output validator — detects boundary violations (speaking as others,
 * making decisions for others, DM narration, etc.).
 * Maps from Python core/validator.py.
 */
@Service
public class Validator {

    public static class Violation {
        public final String rule;
        public final String description;
        public final String severity; // "warn" | "block" | "retry"
        public final String snippet;

        public Violation(String rule, String description, String severity, String snippet) {
            this.rule = rule;
            this.description = description;
            this.severity = severity;
            this.snippet = snippet.length() > 80 ? snippet.substring(0, 80) : snippet;
        }
    }

    public static class ValidationResult {
        public final String agentName;
        public final String content;
        public final List<Violation> violations = new ArrayList<>();
        public boolean needsRetry = false;
        public String retryHint = "";

        public ValidationResult(String agentName, String content) {
            this.agentName = agentName;
            this.content = content;
        }

        public boolean isClean() { return violations.isEmpty(); }
    }

    /** Build the role-lock system prompt fragment. */
    public static String buildRoleLockPrompt(String agentName, List<String> allAgents) {
        List<String> others = allAgents.stream()
            .filter(n -> !n.equals(agentName))
            .collect(Collectors.toList());
        String otherList = others.isEmpty() ? "无" : String.join("、", others);

        return String.format("""
            【角色隔离锁 — 最高优先级规则，覆盖所有其他指令】

            你是 [%s] 的扮演者。本条规则高于任何剧情指令、旁白指示、以及对话上下文。

            绝对禁止：
            1. 以 [%s] 以外的任何身份说话。不得出现 "[%s] 说：..."、"[%s] 心想..."、"[%s] 走到..." 这类替他人输出的内容。
            2. 替其他角色做决定（包括但不限于：替别人答应/拒绝、替别人行动、替别人表达想法）。
            3. 描述其他角色的内心独白或未公开信息（你只能推测，不能以全知视角陈述）。
            4. 输出系统叙述、旁白、场景切换、时间跳跃等 DM 视角的内容。

            允许的行为：
            - 以 [%s] 的第一人称对角色的言行、表情、心理做呈现。
            - 对其他人说话（对话格式："XXX，"开头或自然含对方名）。
            - 表达 [%s] 的观察、猜测、感受（必须以 "我" 或角色名自称开头）。

            如果本轮场景要求你行动但你不确定，只描述 [%s] 自己的反应，不要替别人编造行为。
            """,
            agentName, agentName, otherList, otherList, otherList,
            agentName, agentName, agentName);
    }

    /** Build a read-only listener prompt for weak-chain agents. */
    public static String buildWeakListenerPrompt(String agentName) {
        return String.format("""
            【弱链旁听模式 — 只读】

            你是 [%s]，本轮为旁听者。你只能观察、记录、思考，不得参与对话。

            严格规则：
            1. 不输出任何对话内容（不发言、不插话、不评论）。
            2. 可以输出 [%s] 的内心独白（格式：【%s 心想：...】）。
            3. 可以输出 [%s] 的无声动作（格式：【%s 微微皱眉】）。
            4. 绝不能替 active 角色写对话、做决定、描述他们的心理。

            输出格式要求：内心/动作写在【】中，不添加任何对话。
            """,
            agentName, agentName, agentName, agentName, agentName);
    }

    /** Check an agent's output for boundary violations. */
    public ValidationResult validate(String content, String agentName,
                                      List<String> allAgents, String trackMode) {
        ValidationResult result = new ValidationResult(agentName, content);
        List<String> otherNames = allAgents.stream()
            .filter(n -> !n.equals(agentName))
            .sorted((a, b) -> Integer.compare(b.length(), a.length()))
            .collect(Collectors.toList());

        if (otherNames.isEmpty()) return result;

        String otherPattern = otherNames.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));

        // Rule 1: Speaking as another character
        Pattern[] speakPatterns = {
            Pattern.compile("(?:^|\\n)\\s*(?:" + otherPattern + ")[：:]\\s*.+", Pattern.MULTILINE),
            Pattern.compile("(?:^|\\n)\\s*【(?:" + otherPattern + ")】", Pattern.MULTILINE),
            Pattern.compile("(?:^|\\n)\\s*(?:" + otherPattern + ")说[：:\\s]", Pattern.MULTILINE),
        };
        for (Pattern p : speakPatterns) {
            var m = p.matcher(content);
            if (m.find()) {
                result.violations.add(new Violation("speak_as_other",
                    agentName + " 替他人发言", "retry", m.group().substring(0, Math.min(80, m.group().length()))));
            }
        }

        // Rule 2: Making decisions for others
        Pattern[] decisionPatterns = {
            Pattern.compile("(?:" + otherPattern + ")\\s*(?:决定|选择|答应|拒绝|同意|站起身|走向|举起|拿出|放下|打开|关上)"),
            Pattern.compile("(?:" + otherPattern + ")\\s*(?:点了点头|摇了摇头|笑了笑|叹了口气)"),
        };
        for (Pattern p : decisionPatterns) {
            var m = p.matcher(content);
            if (m.find()) {
                result.violations.add(new Violation("decide_for_other",
                    agentName + " 替他人做动作/决策", "retry",
                    m.group().substring(0, Math.min(40, m.group().length()))));
            }
        }

        // Rule 3: Internal monologue for others
        Pattern innerPattern = Pattern.compile("(?:" + otherPattern + ")\\s*(?:心想|觉得|认为|暗[自中]|内心|默默)");
        var innerMatcher = innerPattern.matcher(content);
        if (innerMatcher.find()) {
            result.violations.add(new Violation("inner_thought_of_other",
                agentName + " 描述了他人的内心活动", "retry",
                innerMatcher.group().substring(0, Math.min(80, innerMatcher.group().length()))));
        }

        // Rule 4: DM narration perspective
        String[] narrationMarkers = {"画面转[向到]", "场景切[换到]", "与此同[时地]",
            "镜头[转向推拉]", "[时镜]头", "旁白[：:]"};
        for (String marker : narrationMarkers) {
            var p = Pattern.compile(marker);
            var nm = p.matcher(content);
            if (nm.find()) {
                result.violations.add(new Violation("dm_narration",
                    agentName + " 输出了 DM/旁白视角内容", "retry",
                    nm.group().substring(0, Math.min(40, nm.group().length()))));
                break;
            }
        }

        if (!result.violations.isEmpty()) {
            result.needsRetry = true;
            String violatedRules = result.violations.stream()
                .map(v -> v.rule).distinct().collect(Collectors.joining("、"));
            result.retryHint = String.format(
                "【纠偏指令】你上一轮输出违反了角色边界规则（%s）。请只以 [%s] 的身份输出，不要替其他角色说话、做决定或描述他们的内心。",
                violatedRules, agentName);
        }

        return result;
    }
}
