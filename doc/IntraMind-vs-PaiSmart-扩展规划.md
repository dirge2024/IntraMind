# IntraMind 阶段性扩展规划（对标 PaiSmart）

> 2026-05-08 | 基于 PaiSmart-main2 完整源码分析

## 一、Agent 核心能力

### 1.1 ReAct 决策循环
- **当前状态**：未实现
- **对标参考**：`ChatHandler.runReActLoop()` — 最多 4 轮推理，每轮模型自主决定调用工具或直接回答，工具结果以 `tool` role 回注 messages，继续下一轮推理
- **优先级**：P0
- **核心作用**：让 LLM 不再是"检索→回答"的单向流水线，而是能多轮思考、主动查库、自我纠错。这是 Agent 级 RAG 与普通 RAG 的分水岭
- **实现思路**：
  - 在现有 `DeepSeekLlmService` 基础上扩展 ReAct 循环
  - 先用 `tool_choice: auto` 让模型决定是否调工具，工具结果以 `role: tool` 回注
  - 设置最大轮次(4)和最大工具调用次数(8)防止无限循环
  - 每轮都检查是否被用户取消

### 1.2 Agent 工具注册与执行
- **当前状态**：未实现
- **对标参考**：`AgentToolRegistry` — 工具定义(name/description/parameters JSON Schema) + 执行分发(Handler Map) + 结果格式化
- **优先级**：P0
- **核心作用**：统一管理 Agent 可调用的工具，支持动态扩展。工具 Schema 直接喂给 LLM 的 tools 参数，模型据此决定调用时机和参数
- **实现思路**：
  - 创建 `AgentToolRegistry`，定义 `AgentTool` record(name, description, parameters)
  - 最少注册 `search_knowledge` 工具（调用现有 RetrievalService）
  - Schema 用 Map 构建即可，无需额外依赖

### 1.3 工具调用状态推送
- **当前状态**：未实现（当前仅 SSE 文本流，无结构化事件）
- **对标参考**：`ChatHandler.sendToolCallStatus()` — 通过 WebSocket 向前端推送 tool_call 事件(type=tool_call, status=executing/success/failed)
- **优先级**：P1
- **核心作用**：前端能展示"正在搜索知识库..."→"搜索完成，正在生成回答..."的过程，提升交互透明度
- **实现思路**：
  - 在现有 SSE 流中增加结构化事件类型（`event: tool_call`）
  - 或用 WebSocket 替代 SSE（更灵活但改动大）

### 1.4 反馈收集与自适应
- **当前状态**：未实现
- **对标参考**：`AgentToolRegistry.submitFeedbackTool()` — 用户评价(good/bad)存入 Redis Hash，`ChatHandler.buildRecentFeedbackGuidance()` 读取最近 5 条反馈注入下次对话的系统提示词
- **优先级**：P1
- **核心作用**：让模型从用户反馈中学习偏好，逐步优化回答风格
- **实现思路**：
  - 前端加点赞/点踩按钮
  - 后端 `submit_feedback` 工具存入 Redis `feedback:{userId}` Hash
  - 下次对话拼 Prompt 时附加最近反馈

### 1.5 生成任务生命周期管理
- **当前状态**：未实现
- **对标参考**：`ChatGenerationStateService` — Redis 追踪每次生成的状态(STREAMING→COMPLETED/FAILED/CANCELLED)、内容和引用映射，TTL 30min
- **优先级**：P1
- **核心作用**：支持停止生成、断线重连恢复、前端轮询生成状态
- **实现思路**：
  - Redis Key: `chat:generation:{id}:meta/content/refs`
  - 状态枚举: STREAMING, COMPLETED, FAILED, CANCELLED

---

## 二、RAG 增强能力

### 2.1 权限感知检索
- **当前状态**：未实现（当前 ES 查询无用户/权限过滤）
- **对标参考**：`HybridSearchService.searchWithPermission()` — ES bool 查询 filter 层做 userId/orgTag/isPublic 三级权限过滤
- **优先级**：P0
- **核心作用**：多用户场景下数据隔离，每人只能搜到自己的文件+公开文件+所属组织文件
- **实现思路**：
  - ES 索引增加 `userId`、`orgTag`、`isPublic` 字段
  - 检索时从 JWT/Token 提取 userId，加 filter 条件

### 2.2 多知识库 / 组织标签
- **当前状态**：未实现（当前仅单一知识库，无组织/标签概念）
- **对标参考**：`OrganizationTag` — 层级标签树，文件关联 orgTag，用户可属于多个组织，检索时自动扩展子标签
- **优先级**：P1
- **核心作用**：支持按部门/项目/团队隔离知识库，是"本地 RAG"到"团队 RAG"的关键一步
- **实现思路**：
  - 新建 `organization_tag` 表(parent_tag 自引用)
  - FileMetadata 增加 orgTag 字段
  - 可用简单字符串标签起步，层级树后续迭代

### 2.3 引用溯源增强
- **当前状态**：部分实现（当前 PromptBuilder 有 `[来源: xxx]` 标注，但无结构化引用映射）
- **对标参考**：`ChatHandler.ReferenceInfo` — 每条引用记录 fileMd5/fileName/pageNumber/anchorText/retrievalMode/score/chunkId，`generationReferenceMappings` 在生成级保存，前端可通过 referenceNumber 反查详情
- **优先级**：P1
- **核心作用**：用户点击引用编号能弹出源文件片段+页码+检索方式，提升可信度
- **实现思路**：
  - 在 RetrievalResult 中保留 fileMd5/pageNumber/score 等字段
  - 检索完成后构建 `referenceMappings` Map（编号→详情）
  - 前端聊天消息增加引用编号点击事件

### 2.4 分块优化
- **当前状态**：部分实现（已有三级降级分块：段落→句子→HanLP，512 字符/15% 重叠）
- **对标参考**：PaiSmart 通过 ParseService 做语义分块 + pageNumber/anchorText 标注
- **优先级**：P2
- **核心作用**：更精准的语义边界识别，减少截断导致的检索遗漏；页码标注便于引用定位
- **实现思路**：
  - 当前 FixedSizeTextChunker 已较完善
  - 可增加 PDF 页码提取(Apache PDFBox)和锚点文本

### 2.5 检索降级策略
- **当前状态**：部分实现（仅有 KNN→BM25→rescore 三级瀑布，未处理 Embedding 失败场景）
- **对标参考**：`HybridSearchService.embedToVectorList()` — 向量生成失败时自动降级为纯文本搜索(textOnlySearch)
- **优先级**：P2
- **核心作用**：Embedding API 故障时不让整个问答链路中断
- **实现思路**：
  - 在 RetrievalService 中加 try-catch，向量化失败时退化为 BM25-only

---

## 三、工具编排

### 3.1 工具 Schema 自动生成
- **当前状态**：未实现
- **对标参考**：`AgentToolRegistry` — `stringSchema()`/`integerSchema()`/`objectSchema()` 构建 OpenAI function-calling 兼容的 JSON Schema
- **优先级**：P0（与 1.2 一起实现）
- **核心作用**：每个工具的定义(参数名/类型/描述/必填)直接决定了 LLM 调用工具的准确性
- **实现思路**：
  - 用 LinkedHashMap 构建 properties/required 结构
  - 无需引入 JSON Schema 库，Map 序列化即可

### 3.2 LLM Provider 路由器
- **当前状态**：部分实现（DeepSeekLlmService 仅支持单一 provider，embedding 模块已有双实现但 llm 没有）
- **对标参考**：`LlmProviderRouter` — 统一流式/非流式接口，`ModelProviderConfigService` 从 DB 读取激活的 provider 配置，支持运行时切换
- **优先级**：P1
- **核心作用**：一套代码适配多个 LLM 提供商（DeepSeek/Qwen/OpenAI/Ollama），用户可在管理页切换
- **实现思路**：
  - 抽象 `LlmProvider` 接口（chatStream, chatReAct）
  - 复用现有 `@ConditionalOnProperty` 或改为 DB 配置驱动
  - 先支持 DeepSeek + Qwen 双实现

### 3.3 工具调用预算控制
- **当前状态**：未实现
- **对标参考**：`ChatHandler.MAX_REACT_TOOL_CALLS = 8` — 超过预算时工具不再执行，直接提示模型基于已有结果回答
- **优先级**：P1
- **核心作用**：防止模型陷入工具调用死循环，控制 Token 消耗
- **实现思路**：
  - 在 ReAct 循环中维护 `executedToolCalls` 计数器
  - 超限时注入 tool message 提示"预算已用尽"

### 3.4 流式取消与资源回收
- **当前状态**：未实现（SSE 无主动取消机制）
- **对标参考**：`LlmProviderRouter.StreamHandle.cancel()` — 通过 WebFlux Disposable 取消上游 SSE 连接，同时 settle 已消耗的 Token 配额
- **优先级**：P1
- **核心作用**：用户点"停止生成"时立即释放服务端资源，不浪费 Token
- **实现思路**：
  - 用 WebClient 替代 RestTemplate 调 LLM API（响应式，可取消）
  - 维护 `activeStreams` Map，取消时 dispose Subscription

---

## 四、工程化能力

### 4.1 频率限制
- **当前状态**：未实现
- **对标参考**：`RateLimitService` — Redis 滑动窗口计数器，支持按用户/按 IP 的多级限流(register/login/chat/embedding)，限流配置存入 DB 支持热更新
- **优先级**：P0
- **核心作用**：防止 API 滥用、控制成本、保障服务质量
- **实现思路**：
  - Redis `INCR` + `EXPIRE` 实现简易固定窗口
  - 先做聊天消息限流(每分钟 N 次)，后续扩展

### 4.2 Token 配额管理
- **当前状态**：未实现
- **对标参考**：`UsageQuotaService` — LLM/Embedding 分项配额，按天预算控制，Token 估算(ASCII 0.3x, CJK 0.95x)，分钟级全网预算防溢出
- **优先级**：P1
- **核心作用**：成本可控，防止单用户耗尽 API 额度
- **实现思路**：
  - 在 LlmService 调用前后做 Token 估算和扣减
  - 先做简单的字符数估算(中文 1 字≈1 token)

### 4.3 可配置提示词
- **当前状态**：部分实现（PromptBuilder 系统提示词硬编码）
- **对标参考**：`LlmProviderRouter.buildReActMessages()` — 从 `AiProperties.Prompt` 读取 rules/refStart/refEnd/noResultText，支持配置文件覆盖
- **优先级**：P1
- **核心作用**：不用改代码就能调整 AI 行为策略（如"优先检索/优先直接回答"的比例）
- **实现思路**：
  - application.yml 增加 `localrag.prompt.rules/noResultText` 配置项
  - PromptBuilder 读取配置组合 Prompt

### 4.4 链路日志
- **当前状态**：部分实现（关键节点有 Slf4j 日志，但缺少结构化请求级追踪）
- **对标参考**：PaiSmart 每个 generation 有唯一 generationId，贯穿 WebSocket→ChatHandler→LlmProviderRouter→AgentToolRegistry 全链路，日志可追踪
- **优先级**：P2
- **核心作用**：排查问题时能按请求 ID 串联所有日志
- **实现思路**：
  - 每次对话生成 generationId，放入 MDC
  - Logback 配置 `%X{generationId}` 输出

### 4.5 线程池隔离
- **当前状态**：未实现（Tomcat 线程直接处理 LLM 流）
- **对标参考**：`ChatHandler` 将 ReAct 循环提交到 `chatMonitorExecutor` 独立线程池，避免阻塞 WebSocket 线程
- **优先级**：P2
- **核心作用**：长时间 LLM 调用不占用 HTTP/WS 线程，提高并发吞吐
- **实现思路**：
  - 配置 `ThreadPoolTaskExecutor`(core=4, max=8, queue=50)
  - SSE 流式响应异步执行

### 4.6 异常兜底
- **当前状态**：未实现（异常直接返回错误）
- **对标参考**：PaiSmart 搜索时 Embedding 失败→textOnlySearch 降级；`reindexDocument` 失败→标记 VECTORIZATION_FAILED 允许重试
- **优先级**：P2
- **核心作用**：单点故障不中断整体服务
- **实现思路**：
  - 检索降级：向量失败→纯文本
  - 文档处理降级：解析失败→标记错误状态→允许重试

---

## 五、会话与用户管理

### 5.1 用户认证与权限
- **当前状态**：未实现（无登录/注册，无权限控制）
- **对标参考**：Spring Security + JWT，`User` 实体(USER/ADMIN 角色)，`JwtUtils` 生成/验证 Token，`SecurityConfig` 保护 API
- **优先级**：P0
- **核心作用**：多用户场景的基础，所有后续权限功能的前提
- **实现思路**：
  - 创建 User 实体 + JPA Repository
  - Spring Security + JWT Filter
  - 注册/登录 API

### 5.2 会话持久化增强
- **当前状态**：部分实现（ChatSession + ChatHistoryMessage 已有 JPA + Redis 双层存储，但缺少 ConversationSession 概念）
- **对标参考**：`ConversationSession` — ACTIVE/ARCHIVED 状态，独立的会话管理(创建/切换/归档/重命名)，与 Conversation(问答记录)分离
- **优先级**：P1
- **核心作用**：Session 是对话容器(标题+状态)，Conversation 是对话内容，分离后支持归档/恢复/多会话并存
- **实现思路**：
  - 提取现有的 ChatSession → 对齐为 ConversationSession
  - ChatHistoryMessage → 对齐为 Conversation
  - 增加 ARCHIVED 状态、归档/取消归档 API

### 5.3 用户画像
- **当前状态**：未实现
- **对标参考**：PaiSmart 通过 orgTag 关联用户-组织，通过 recharge/token 记录追踪使用量
- **优先级**：P2
- **核心作用**：了解用户使用模式，为个性化推荐做准备
- **实现思路**：
  - 先做简单的使用统计(上传文件数/问答次数/Token 消耗)
  - 后续再做偏好分析

### 5.4 对话历史引用持久化
- **当前状态**：未实现（当前聊天记录仅存 role+content，引用信息嵌入文本后丢失）
- **对标参考**：`Conversation.referenceMappingsJson` — 助手回复的结构化引用映射以 JSON 持久化到 MySQL
- **优先级**：P1
- **核心作用**：查看历史对话时仍能点击引用编号跳转到源文件
- **实现思路**：
  - ChatHistoryMessage 增加 `referenceMappingsJson` 字段(LONGTEXT)

---

## 六、其他扩展能力

### 6.1 WebSocket 实时通信
- **当前状态**：未实现（当前用 SSE，单向流）
- **对标参考**：`ChatWebSocketHandler` — WebSocket 双向通信，支持心跳、JWT 鉴权、停止指令、结构化事件(start/chunk/tool_call/completion/error/stop)
- **优先级**：P1
- **核心作用**：前端能主动取消生成，服务端能推送结构化事件(非纯文本流)
- **实现思路**：
  - 新增 `ChatWebSocketHandler`，注册 `WebSocketConfig`
  - JWT 通过 URL 参数传递
  - 保持 SSE 兼容，WebSocket 作为增强选项

### 6.2 多模型 Provider 管理
- **当前状态**：部分实现（embedding 已有 Qwen/DeepSeek 双实现，llm 仅 DeepSeek）
- **对标参考**：`ModelProviderConfigService` — DB 表驱动，支持 llm/embedding 双 scope，每 scope 可配多个 provider，运行时切换激活
- **优先级**：P1
- **核心作用**：管理页切换模型无需重启，支持添加自定义 OpenAI 兼容 provider
- **实现思路**：
  - llm 模块参考 embedding 做 `@ConditionalOnProperty` 双实现
  - 后续升级为 DB 配置驱动

### 6.3 用户 Token 余额与充值
- **当前状态**：未实现
- **对标参考**：`RechargeService` + `RechargeOrder` + `RechargePackage` — 充值套餐→订单→Token 余额→消费扣减→`UserTokenRecord` 流水
- **优先级**：P2
- **核心作用**：商业化基础，按量计费
- **实现思路**：
  - 先做简单的 Token 计数(每人每天限额)
  - 充值系统复杂度高，放在最后阶段

### 6.4 使用监控面板
- **当前状态**：未实现
- **对标参考**：`UsageDashboardService` — 用户级/系统级用量统计(对话次数、Token 消耗、活跃用户、每日趋势)
- **优先级**：P2
- **核心作用**：运维视角了解系统健康度和使用情况
- **实现思路**：
  - 基于 `UserDailyChatCount` 做简单统计
  - 管理端页面展示

### 6.5 PDF 预览
- **当前状态**：未实现
- **对标参考**：`DocumentService.getPdfSinglePagePreview()` — PDFBox 提取单页渲染为图片，本地内存缓存+Redis Base64 缓存双层
- **优先级**：P2
- **核心作用**：知识库浏览时不用下载就能预览 PDF 内容
- **实现思路**：
  - 用 PDFBox 提取指定页
  - 先做内存缓存，后续加 Redis

### 6.6 文件重处理
- **当前状态**：未实现（文件上传后无法单独重试向量化）
- **对标参考**：`DocumentService.reindexDocument()` / `enqueueAsyncVectorizationRetry()` — 支持同步重建索引和异步 Kafka 重试，向量化状态追踪
- **优先级**：P2
- **核心作用**：向量化失败后支持重试，不用重新上传文件
- **实现思路**：
  - FileMetadata 增加 `vectorizationStatus` 字段(PENDING/PROCESSING/COMPLETED/FAILED)
  - 新增 `POST /api/storage/reindex/{md5}` 端点

---

## 七、优先级汇总

### P0 — 核心瓶颈，优先实现
| 序号 | 功能点 | 模块 |
|------|--------|------|
| 1 | 用户认证与权限(JWT + Spring Security) | 会话与用户管理 |
| 2 | ReAct 决策循环 | Agent 核心能力 |
| 3 | Agent 工具注册与执行(search_knowledge) | Agent 核心能力 |
| 4 | 权限感知检索(userId/orgTag 过滤) | RAG 增强 |
| 5 | 频率限制(Redis 滑动窗口) | 工程化能力 |

### P1 — 显著提升体验，第二批实现
| 序号 | 功能点 | 模块 |
|------|--------|------|
| 6 | LLM Provider 路由器(DeepSeek + Qwen) | 工具编排 |
| 7 | 工具调用状态推送 | Agent 核心能力 |
| 8 | 引用溯源增强(结构化映射) | RAG 增强 |
| 9 | 反馈收集与自适应 | Agent 核心能力 |
| 10 | 生成任务生命周期管理 | Agent 核心能力 |
| 11 | Token 配额管理 | 工程化能力 |
| 12 | 可配置提示词 | 工程化能力 |
| 13 | 多知识库/组织标签 | RAG 增强 |
| 14 | 工具调用预算控制 | 工具编排 |
| 15 | 流式取消与资源回收 | 工具编排 |
| 16 | 会话持久化增强(ConversationSession) | 会话与用户管理 |
| 17 | 对话历史引用持久化 | 会话与用户管理 |
| 18 | WebSocket 实时通信 | 其他 |
| 19 | 多模型 Provider 管理 | 其他 |

### P2 — 锦上添花，第三批实现
| 序号 | 功能点 | 模块 |
|------|--------|------|
| 20 | 分块优化(页码+锚点) | RAG 增强 |
| 21 | 检索降级策略 | RAG 增强 |
| 22 | 链路日志(MDC) | 工程化能力 |
| 23 | 线程池隔离 | 工程化能力 |
| 24 | 异常兜底 | 工程化能力 |
| 25 | 用户画像 | 会话与用户管理 |
| 26 | PDF 预览 | 其他 |
| 27 | 文件重处理 | 其他 |
| 28 | Token 余额与充值 | 其他 |
| 29 | 使用监控面板 | 其他 |

---

## 八、实施建议

1. **P0 三个功能点本质上是一体的**：先有用户体系 → 检索才能做权限过滤 → Agent 的 ReAct 循环才能关联 userId。建议用 1-2 个迭代一口气完成。

2. **不强依赖 WebSocket**：PaiSmart 用 WebSocket，但 IntraMind 可以继续用 SSE + 增强事件类型(参考 SSE event 字段)，降低改动成本。

3. **组织标签可以简化为单标签字符串**：PaiSmart 的层级标签树是企业级需求，学习阶段先用文件夹/项目名作为标签即可。

4. **Token 配额先用简单方案**：按用户每日请求次数限制，不需要完整的充值系统。

---

## 九、附录：与 PaiSmart 核心能力对比

| 维度 | PaiSmart | IntraMind 当前 |
|------|----------|---------------|
| Agent 框架 | ReAct 循环（4 轮/8 次 tool_call） | 无，检索→Prompt→回答 |
| 工具注册 | 4 个 Agent Tool | 无 |
| 通信方式 | WebSocket（JWT + 心跳 + stop） | SSE（单向流式） |
| 权限体系 | Spring Security + JWT + 多租户 RBAC | 无 |
| 模型管理 | DB 驱动 Provider 切换 + API Key 加密 | 静态 @ConditionalOnProperty |
| 限流配额 | 用户级 + 全局级 Token 预算 | 无 |
| 混合检索 | KNN→权限过滤→BM25 rescore | KNN→BM25→rescore |
| 引用溯源 | MD5/文件名/页码/锚文本/检索模式/得分 | 仅 chunkId/md5/text/score |
| 会话管理 | CRUD + 归档 + 自动标题 | CRUD + 置顶 + 搜索 |
| 对话存储 | MySQL 事务优先 → Redis 缓存 | Redis + MySQL 双层 |
| 用户反馈 | good/bad → Redis → 注入 Prompt | 无 |
| Prompt 配置 | rules/refStart/refEnd/noResultText 可配 | 硬编码 |

---

## 十、附录：关键设计决策（来自 PaiSmart 分析）

1. **ReAct 消息格式**：使用 OpenAI tool_calls 格式（`role: assistant, tool_calls: [...]` → `role: tool, tool_call_id, content`），兼容多模型
2. **工具流式输出**：`generate_summary` 内部 LLM 摘要的 token 直接流到前端，外层收到 `streamedToUser=true` 后直接 finalize
3. **历史消息压缩**：ReAct 上下文仅保留最近 6 条（每条约 800 字），防 Prompt 膨胀
4. **搜索引用编号**：每次 `search_knowledge` 重新生成 `[1]...[K]` 编号，按 generation 覆盖映射
5. **MySQL-first 持久化**：聊天记录先落 MySQL（事务），成功后再写 Redis，保证一致性
6. **Token 预算预占**：先预占（Redis INCR），实际消耗后结算差值，支持精确核算

---

## 十一、附录：架构扩展路线

```
common → storage → messaging → document → embedding → vector-store → retrieval → llm → agent → api
                                                                                      ↑ 新增
```

- **agent 模块**：插在 llm 和 api 之间，依赖 retrieval + llm
- **auth**：合入 api 模块（简单场景）或独立模块
- **模型 Provider**：扩展 embedding 的抽象到 llm 层
- **WebSocket**：在 api 模块新增 Handler
