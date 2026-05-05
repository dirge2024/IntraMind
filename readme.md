## LocalRAG

本地 RAG 知识库系统。

### 项目结构

```
├── backend/              # Java 后端（Spring Boot 3.3.5 + Maven 多模块）
│   ├── api/              # 唯一启动入口
│   ├── common/           # 公共
│   ├── storage/          # MinIO + Redis 存储
│   ├── messaging/        # Kafka
│   ├── document/         # 文档处理
│   ├── embedding/        # 向量化
│   ├── vector-store/     # ES 向量库
│   ├── retrieval/        # 检索
│   └── llm/              # 大模型
├── frontend/             # 统一测试页面
├── docker-compose.yml    # MySQL + Redis + Kafka + MinIO + ES
└── 需求分析/              # 开发文档
```

### 启动

```bash
# 1. 启动中间件（Docker Desktop 需在运行）
docker compose up -d

# 2. 构建 + 启动后端
cd backend
mvn clean install -DskipTests
mvn spring-boot:run -pl api
```

### 访问

| 地址 | 说明 |
|------|------|
| http://localhost:8088 | 测试控制台 |
| http://localhost:8088/upload.html | 文件上传测试 |
| http://localhost:8088/api/health | 健康检查 |
| http://localhost:19001 | MinIO 控制台（dirge / wsy2642@） |
