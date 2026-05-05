# LocalRAG 项目开发规范

## 技术栈
- Java 17, Spring Boot 3.x, Maven 多模块
- 存储: MinIO (Docker, 19000/19001, dirge/wsy2642@)
- 缓存: Redis (Docker, 6379, wsy2642@)
- 数据库: MySQL (Docker, 3307, root/wsy2642@)
- 消息: Kafka (Docker, 9092, 3 个预建 topic)
- 向量库: Elasticsearch 8.x (Docker, 9200, elastic/wsy2642@)
- 模型: 通义千问 / DeepSeek（统一接口，可切换）
- 所有中间件统一 `docker compose up -d` 启动，密码均为 wsy2642@

## 项目结构
common → storage → messaging → document → embedding → vector-store → retrieval → llm → api
后层可依赖前层，禁止反向依赖。

## 编码规范
- 面向接口编程，每层 contract/ 放接口，impl/ 放实现
- embedding和llm模块必须抽象，支持通义千问/DeepSeek双实现
- 配置走 application.yml，敏感信息用环境变量
- 统一返回体 Result<T>，统一异常处理
- 日志用 Slf4j，关键节点必须打日志
- REST API 用 SSE 做流式输出
- 不写多余注释，命名即文档

## 实现决策
- MinIO 8.5.7 API 使用 composeObject 服务端合并分片，各分片为独立对象
- 上传进度用 Redis Hash 追踪，key=upload:{md5}
- 文件标识用 MD5，支持秒传和断点续传
- 元数据暂存 ConcurrentHashMap，后续切 MySQL

## 开发顺序
1. docker-compose 拉基础设施
2. 父POM + common模块
3. 按数据流顺序逐个模块开发
4. 每个模块写完自测通过再进入下一个
