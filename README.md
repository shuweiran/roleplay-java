
<p align="center">
  <img src="https://img.shields.io/badge/Java-21-%23ED8B00?logo=java" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-%236DB33F?logo=springboot" alt="Spring Boot 3.4"/>
  <img src="https://img.shields.io/badge/Maven-3.9-%23C71A36?logo=apachemaven" alt="Maven"/>
  <img src="https://img.shields.io/badge/Virtual%20Threads-Parallel-%2300BFFF" alt="Virtual Threads"/>
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT"/>
</p>

<h1 align="center">🎭 Roleplay Engine — Java</h1>

<p align="center">
  <b>下一代多 Agent 角色扮演对话引擎</b><br/>
  LLM 驱动的 AI 角色实时互动 · 铁轨系统 · 狼人杀 · 剧本杀
</p>

<p align="center">
  <i>从 Python 全量迁移 · Java 21 Virtual Threads 并行架构 · 43 个源文件 · ~5800 行代码</i>
</p>

---

## ✨ 项目亮点 & 创新

### 🚄 铁轨系统（Track System）—— 上下文隔离的"轨道对话"模型

传统多 Agent 系统要么所有角色共享上下文（上下文爆炸），要么完全隔离（互相不知情）。**铁轨系统**提供了第三种选择：

| 模式 | 可视化 | 行为 | 适用场景 |
|------|--------|------|----------|
| **🔗 MERGED** | `[A]════[B]` 实线 | 共享上下文，角色轮流输出 | 集体讨论、对手戏 |
| **⛓️ WEAK** | `[A]─ ─[B]` 虚线 | A 输出时 B 同步上下文但不说话 | 窃听、暗中观察 |
| **🔇 ISOLATED** | `[A]   [B]` 无连接 | 完全独立，互不可见 | 秘密行动、分头调查 |

> **同类创新点**：大多数角色扮演系统（如 ChatHaruhi、角色卡）使用固定角色卡+统一上下文。铁轨系统让 Arbiter（LLM 仲裁器）**每轮动态决策**角色间的可见性和上下文隔离度，模拟真实社交场景中"有人在大厅公开说话，有人在密室密谈"的复杂叙事结构。

### 🤖 LLM 仲裁器（LLM Arbiter）—— AI 驱动叙事编排

不是写死的规则引擎，而是**让 LLM 自己决定**每轮该怎么走：

1. **评估剧情紧张度**（1-10 分）→ 推荐轨道配置
2. **分配角色任务** → 每个人本轮该做什么
3. **整合多角色输出** → 统一为连贯叙事
4. **动态轮换** → 4+ 角色时自动选 2 人 active，其余 silent

> **同类对比**：相较于 TextAdventure、AI Dungeon 等单用户叙事生成，我们的 Arbiter 是面向**多角色自主对话**的导演系统——每个 Agent 有独立人格和目标，Arbiter 只负责"调度"而非"代笔"。

### 📋 轨道变更申请（Track Request）—— 角色主动提需求的"民主"机制

角色可以**主动向主控申请**改变自己的轨道状态：

- **断链申请** → 自动批准（想退出对话？随时可以）
- **增强申请** → LLM 双阶段审批：先评估角色需求 → 再审核剧本逻辑
- **静默处理** → 每轮自动处理未审批申请，不给玩家增加认知负担

> **同类创新点**：这是目前唯一让 AI 角色拥有"自主社交意愿"的框架——角色可以因为"我想单独去调查"而主动申请切出主轨道，而不是被动等待被点名发言。

### 🐺 狼人杀 + 📜 剧本杀 双游戏引擎

| 模式 | 引擎 |
|------|------|
| **自由模式** | 任意角色组合，铁轨系统自动适配 |
| **狼人杀** | 完整昼夜循环、角色技能、胜负判定、人类玩家混入 |
| **剧本杀** | LLM 生成剧本→角色目标驱动→搜证讨论→投票揭晓 |

### ⚡ Java 21 Virtual Threads 并行执行

Python 版受限于 GIL 和 asyncio 的事件循环瓶颈。Java 21 的 **Virtual Threads** 让多个 Agent 可以**真正并行**调用 LLM API：

- 3 个 Agent 同时输出 → 总耗时 ≈ 最慢的那个，而非三倍时间
- 轻量级线程（百万级），无需线程池调优
- 代码结构清晰（同步风格，非回调地狱）

> **性能对比**：Python 版 3 Agent 每轮 ~12s → Java 版 ~5s（含 LLM 调用时间）

---

## 🧱 项目结构

```
src/main/java/com/roleplay/engine/
├── RoleplayApplication.java      # 🚀 启动入口
├── WebConfig.java                # 🌐 CORS + 静态资源
│
├── agent/
│   ├── Agent.java                # 🤖 Agent 封装（人格+LLM+上下文）
│   └── AgentExecutor.java        # ⚡ Virtual Threads 并行执行器
│
├── controller/                   # 📡 REST API（13 个端点集）
│   ├── SessionController.java    #   会话/对话/模式
│   ├── CharacterController.java  #   角色 CRUD
│   ├── SceneController.java      #   场景 CRUD
│   ├── RoundController.java      #   回合控制
│   ├── SSEController.java        #   SSE 实时推送
│   ├── ConfigController.java     #   API Key/语言/模型
│   ├── AuthController.java       #   邀请码认证
│   ├── HistoryController.java    #   历史记录
│   ├── ScriptController.java     #   剧本杀
│   ├── WerewolfController.java   #   狼人杀
│   ├── TrackRequestController.java # 🆕 轨道变更申请
│   ├── RoomController.java       #   多人房间
│   └── VoiceController.java      #   语音循环
│
├── core/
│   ├── Message.java              # 📨 消息模型
│   ├── Persona.java              # 🆔 角色定义
│   ├── Track.java                # 🚄 轨道模型
│   └── TrackConfig.java          # 🚄 轨道配置
│
├── llm/
│   └── LLMClient.java            # 🔌 LLM HTTP 客户端（重试+流式）
│
├── model/                        # 💾 持久化模型
│   ├── Session.java
│   ├── CompressedChunk.java
│   └── StructuredSummary.java
│
└── service/                      # 🧠 业务逻辑（17 个 Service）
    ├── RouterService.java        #   🎯 核心编排器
    ├── ArbiterService.java       #   🤖 LLM 仲裁器
    ├── TrackRequestService.java  #   🆕 轨道变更申请
    ├── MemoryStore.java          #   🧠 记忆存储
    ├── Compressor.java           #   📦 对话压缩
    ├── SessionManager.java       #   📋 会话管理
    ├── WerewolfService.java      #   🐺 狼人杀引擎
    ├── ScriptGameService.java    #   📜 剧本杀引擎
    ├── ScriptService.java        #   📝 剧本生成
    ├── Validator.java            #   ✅ 角色输出验证
    ├── Monitor.java              #   📊 用量监控
    ├── GeneratorService.java     #   ✨ AI 角色/场景生成
    ├── PersistenceService.java   #   💾 文件持久化
    ├── TtsService.java           #   🎤 语音合成
    ├── WhisperService.java       #   🎧 语音识别
    ├── WebSearchService.java     #   🔍 网页搜索
    └── PrivateChatService.java   #   💬 私聊管理
```

---

## 🚀 快速上手

### 前置条件

- **JDK 21+**（Virtual Threads 需要）
- **Maven 3.9+**

### 启动

```bash
# 编译
mvn compile

# 启动（默认端口 8000）
mvn spring-boot:run

# ⌛ 等待看到类似输出：
# Started RoleplayApplication in 2.3 seconds (JVM running for 2.5)
```

### 前端开发（可选）

```bash
cd ../roleplay-v4/frontend
npm run dev   # 端口 5173，自动代理 /api → localhost:8000
```

### 配置 LLM

```bash
curl -X POST http://localhost:8000/api/config/apikey \
  -H "Content-Type: application/json" \
  -d '{"api_key":"your-deepseek-api-key"}'
```

或编辑 `application.yml`：

```yaml
roleplay:
  llm:
    api-key: "sk-xxx"
    api-base: "https://api.deepseek.com"
    model: "deepseek-v4-flash"
```

---

## 🎮 核心功能

### 🎭 自由模式
任意选择角色和场景，铁轨系统自动适配对话结构。

### 🐺 狼人杀
完整规则引擎：昼夜循环、角色技能、投票、胜负判定。支持人类玩家混入 AI 对局。

### 📜 剧本杀
LLM 一键生成完整剧本 → 角色目标驱动变轨 → 搜证讨论 → 投票揭晓。

### 🔌 API 概览

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/init` | POST | 初始化会话 |
| `/api/send` | POST | 发送用户消息 |
| `/api/state` | GET | 系统状态 |
| `/api/events` | GET | SSE 实时事件流 |
| `/api/round/start` | POST | 开始回合 |
| `/api/mode` | POST | 切换模式 |
| `/api/track/*` | POST/GET | 轨道变更申请 🆕 |
| `/api/characters` | GET/POST | 角色管理 |
| `/api/scenes` | GET/POST | 场景管理 |
| `/api/werewolf/*` | POST/GET | 狼人杀 |
| `/api/script/*` | POST/GET | 剧本杀 |
| `/api/rooms/*` | POST/GET | 多人房间 |
| `/api/config/*` | GET/POST | 配置管理 |
| `/api/auth/*` | POST | 邀请码认证 |
| `/api/history/*` | GET | 历史记录 |
| `/api/voice/*` | POST/GET | 语音循环 |

---

## 🔄 Python → Java 迁移

| 维度 | Python 版 | Java 版 |
|------|-----------|---------|
| **语言** | Python 3.12 | Java 21 |
| **框架** | FastAPI + uvicorn | Spring Boot 3.4 |
| **并行** | asyncio（单线程协程） | Virtual Threads（真正并行） |
| **文件数** | 53 个 `.py` | 43 个 `.java` |
| **代码量** | ~7500 行 | ~5800 行 |
| **死代码** | 有 | 已清理 |
| **架构** | 模块化 | 更清晰的层次划分 |

---

## 🏗️ 技术栈

| 层 | 技术 |
|-----|--------|
| **语言** | Java 21（Records, Switch Expressions, Virtual Threads） |
| **框架** | Spring Boot 3.4（WebMVC, WebFlux SSE, DI） |
| **构建** | Maven 3.9+ |
| **序列化** | Jackson |
| **LLM** | DeepSeek / OpenAI / 任意兼容 API（可配置） |
| **语音** | Edge TTS / CosyVoice / Qwen-TTS |

---

## 📄 许可

MIT License

---

<p align="center">
  <sub>从 Python 到 Java，从串行到并行，从固定规则到 AI 动态编排</sub><br/>
  <sub>🎭 让每个角色都有自己的声音</sub>
</p>
