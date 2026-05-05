## LocalRAG

本地 RAG 知识库系统。

### 模块

| 文件夹 | 说明 |
|--------|------|
| `backend/` | Java 后端，Spring Boot 3.3.5 + Maven 多模块 |
| `frontend/` | 测试页面，直接放在这里 |
| `docker-compose.yml` | 基础设施：MySQL + Redis + Kafka + MinIO + ES |

### 启动方式

```bash
docker compose up -d                             # 启动中间件
cd backend && mvn clean compile -pl api spring-boot:run   # 启动后端
```

打开浏览器：`http://localhost:8080`
