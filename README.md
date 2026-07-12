# Roleplay Engine — Java

角色扮演多 Agent 对话引擎（Java/Spring Boot 版）。

从 Python（roleplay-v4）迁移而来，核心引擎重写为 Java 21 Virtual Threads 并行架构。

## 项目结构

```
roleplay-java/
├── pom.xml                          # Maven 构建（Spring Boot 3.4 + JDK 21）
├── src/main/java/com/roleplay/engine/
│   ├── RoleplayApplication.java     # 启动入口
│   ├── WebConfig.java               # 静态资源+CORS 配置
│   ├── agent/
│   │   ├── Agent.java               # Agent 封装（角色+LLM+上下文构建）
│   │   └── AgentExecutor.java       # Virtual Threads 并行执行器
│   ├── config/
│   │   └── AppConfig.java           # 全引擎配置（API/模型/代理）
│   ├── controller/                  # REST API 层（12 个 Controller）
│   │   ├── SessionController.java   # 会话管理/对话/模式
│   │   ├── CharacterController.java # 角色 CRUD
│   │   ├── SceneController.java     # 场景 CRUD
│   │   ├── RoundController.java     # 回合控制
│   │   ├── SSEController.java       # SSE 实时推送
│   │   ├── ConfigController.java    # API Key/语言/模型配置
│   │   ├── AuthController.java      # 邀请码认证
│   │   ├── HistoryController.java   # 历史记录
│   │   ├── ScriptController.java    # 剧本杀游戏
│   │   ├── WerewolfController.java  # 狼人杀游戏
│   │   ├── TrackRequestController.java # 轨道变更申请
│   │   ├── RoomController.java      # 多人房间
│   │   └── VoiceController.java     # 语音循环
│   ├── core/
│   │   ├── Message.java             # 消息模型
│   │   ├── Persona.java             # 角色定义
│   │   ├── Track.java               # 轨道系统
│   │   └── TrackConfig.java         # 轨道配置
│   ├── llm/
│   │   └── LLMClient.java           # LLM HTTP 客户端（重试+流式）
│   ├── model/
│   │   ├── Session.java             # 会话持久化模型
│   │   ├── CompressedChunk.java     # 压缩记忆块
│   │   └── StructuredSummary.java   # 结构化摘要
│   └── service/                     # 业务逻辑层
│       ├── RouterService.java       # 核心编排器
│       ├── ArbiterService.java      # LLM 仲裁器（轨道配置+输出整合）
│       ├── MemoryStore.java         # 记忆存储
│       ├── Compressor.java          # 对话压缩
│       ├── Monitor.java             # 用量监控
│       ├── Validator.java           # 角色输出验证
│       ├── GeneratorService.java    # AI 角色/场景生成
│       ├── TrackRequestService.java # 轨道变更申请
│       ├── WerewolfService.java     # 狼人杀引擎
│       ├── ScriptGameService.java   # 剧本杀引擎
│       ├── ScriptService.java       # 剧本生成
│       ├── SessionManager.java      # 会话管理
│       ├── PersistenceService.java  # 文件持久化
│       ├── TtsService.java          # 语音合成
│       ├── WhisperService.java      # 语音识别
│       ├── WebSearchService.java    # 网页搜索
│       ├── PrivateChatService.java  # 私聊管理
│       └── ...
└── src/main/resources/
    └── application.yml              # Spring Boot 配置
```

## 快速启动

```bash
# 前置条件
# - JDK 21+
# - Maven 3.9+

# 编译
mvn compile

# 启动（端口 8000）
mvn spring-boot:run

# 前端开发（另一个终端）
cd ../roleplay-v4/frontend
npm run dev    # 端口 5173，自动代理 /api → :8000
```

## 核心功能

### 多 Agent 对话引擎
- **并行执行**：Java 21 Virtual Threads 同时调用多个 Agent
- **轨道系统**：MERGED / WEAK / ISOLATED 三种上下文隔离模式
- **角色轮换**：4+ 角色时自动轮换 active/silent

### 轨道变更申请系统
- 角色可申请断链（自动批）或增强（需 LLM 审批）
- LLM 双阶段：先评估角色需求 → 再审核剧本逻辑
- 静默处理：每轮自动处理未审批申请

### 狼人杀
- 完整昼夜循环：狼人杀/预言家/女巫/猎人/保护
- 胜负判定：狼人 ≥ 村民 → 狼人胜
- 支持自定义角色分配

### 剧本杀
- 5 阶段游戏：设定→搜证→讨论→投票→揭晓
- LLM 生成剧本（主题/背景/角色/线索/真相）

### API 端点
| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/init` | POST | 初始化会话 |
| `/api/state` | GET | 系统状态 |
| `/api/send` | POST | 发送消息 |
| `/api/round/start` | POST | 开始回合 |
| `/api/mode` | POST | 切换模式（free/protagonist/director/werewolf） |
| `/api/events` | GET | SSE 实时事件流 |
| `/api/werewolf/*` | POST/GET | 狼人杀 |
| `/api/script/*` | POST/GET | 剧本杀 |
| `/api/track/*` | POST/GET | 轨道变更申请 |
| `/api/characters` | GET/POST | 角色管理 |
| `/api/config/*` | GET/POST | 配置管理 |
| `/api/auth/*` | POST | 认证 |
| `/api/rooms/*` | POST/GET | 多人房间 |
| `/api/history/*` | GET | 历史记录 |

## LLM 配置

启动后通过 API 设置 API Key：

```bash
curl -X POST http://localhost:8000/api/config/apikey \
  -H "Content-Type: application/json" \
  -d '{"api_key":"sk-your-key-here"}'
```

或编辑 `application.yml`：
```yaml
roleplay:
  llm:
    api-key: "sk-your-key-here"
    api-base: "https://api.deepseek.com"
    model: "deepseek-v4-flash"
```

## 迁移自

此项目是 [roleplay-v4](https://github.com/)（Python FastAPI）的 Java 全量迁移。
- **Python 版**：43 个后端文件，~7500 行
- **Java 版**：43 个源文件，~5800 行
- **改进**：Virtual Threads 并行、更干净的架构、去掉死代码

## 技术栈
- **Java 21** — Virtual Threads、Records、Switch Expressions
- **Spring Boot 3.4** — WebMVC、WebFlux（SSE）、DI
- **Maven** — 依赖管理、构建
- **Jackson** — JSON 序列化
