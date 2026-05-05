# LocalRAG 开发总览

## 模块列表

| 序号 | 模块 | 状态 | 备注 |
|------|------|------|------|
| 01 | 框架初始化 | ✅ | docker-compose + 父POM + 模块骨架 |
| 02 | common | 🔲 | Result / Exception / 工具类 |
| 03 | storage | 🔲 | MinIO 集成 |
| 04 | messaging | 🔲 | Kafka 集成 |
| 05 | document | 🔲 | 文档解析 / 分块 |
| 06 | embedding | 🔲 | 向量化抽象（通义/DeepSeek） |
| 07 | vector-store | 🔲 | ES 向量存储 / 检索 |
| 08 | retrieval | 🔲 | 检索管线 / 重排序 |
| 09 | llm | 🔲 | 大模型调用（通义/DeepSeek） |
| 10 | api | 🔲 | REST API / SSE 流式 |

## 状态图例
- 🔲 待开发
- 🔄 开发中
- ✅ 已完成
