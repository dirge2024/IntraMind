# LocalRAG 开发总览

## 模块列表

| 序号 | 模块 | 状态 | 说明 |
|------|------|------|------|
| 01 | 框架初始化 | ✅ | docker-compose + 父POM + 模块骨架 |
| 02 | storage | ✅ | MinIO composeObject + Redis 进度追踪 + MySQL 持久化 |
| 03 | messaging | ✅ | Kafka 生产者封装 |
| 04 | document | ✅ | Tika 解析 + 三级降级分块 + 状态追踪与去重 |
| 05 | embedding | ✅ | Qwen v4 / DeepSeek 双实现，每批 10 条，1024 维 |
| 06 | vector-store | ✅ | ES dense_vector + IK 分词 |
| 07 | retrieval | ✅ | KNN→BM25→rescore 三级瀑布检索 |
| 08 | llm | ✅ | DeepSeek SSE 流式 + 会话管理 + 聊天记录双层存储 |
| 09 | 前端页面 | ✅ | 侧边栏四入口 + 五页面统一布局 + common.css |
| 10 | agent | 📋 | ReAct 决策循环 + Tool 注册 + 生成状态管理 + 流式取消 |
| 11 | auth | 📋 | JWT 认证 + 权限感知检索 + 组织标签 + 多知识库 |
| 12 | engineering | 📋 | 限流配额 + Provider 路由 + 可配置 Prompt + 链路日志 + 异常兜底 |

## 文档结构

每个模块目录包含：

- `需求分析.md` — 核心业务逻辑、关键决策、边界条件、技术难点、输入输出规范
- `技术实现方案.md` — 依赖、接口设计、核心流程、配置、文件清单

通用文档（提交模板、开发问题记录）位于项目根目录 `doc/`。

扩展规划总体蓝图见 `doc/IntraMind-vs-PaiSmart-扩展规划.md`。
