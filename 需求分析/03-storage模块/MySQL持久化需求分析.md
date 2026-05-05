# MySQL 持久化 — 需求分析

## 背景

当前 FileMetadata 存储在 `InMemoryFileMetadataRepository`（ConcurrentHashMap），应用重启数据全部丢失。需要接入 MySQL，确保文件元数据持久化。

## 核心逻辑

用 Spring Data JPA 替代内存 Map。file_metadata 表以 md5 为主键。

### 去重规则调整

| 场景 | 旧逻辑 | 新逻辑 |
|------|--------|--------|
| 相同 MD5 + 相同文件名 | 秒传 | 秒传（不变） |
| 相同 MD5 + **不同文件名** | 秒传（返回旧文件名） | **覆盖更新**：旧 MinIO 对象删除，上传新对象，更新 MySQL 记录 |
| 不同 MD5 | 新上传 | 新上传（不变） |

这样用户用不同文件名上传相同内容的文件，元数据会更新为新文件名。

## 关键决策

| 决策点 | 方案 |
|--------|------|
| ORM | Spring Data JPA + Hibernate |
| DDL | `spring.jpa.hibernate.ddl-auto=update` 自动建表 |
| 主键 | md5（VARCHAR 32） |
| 连接池 | HikariCP（Spring Boot 默认） |
| 数据库名 | `localrag`（手动创建或 `createDatabaseIfNotExist=true`） |
| 替代旧实现 | `InMemoryFileMetadataRepository` → `JpaFileMetadataRepository` |

## 表结构

```sql
CREATE TABLE file_metadata (
    md5          VARCHAR(32)  PRIMARY KEY,
    file_name    VARCHAR(500) NOT NULL,
    file_size    BIGINT       NOT NULL,
    object_key   VARCHAR(600) NOT NULL,
    bucket       VARCHAR(100) NOT NULL,
    content_type VARCHAR(200),
    status       VARCHAR(20)  NOT NULL DEFAULT 'READY',
    created_at   DATETIME     NOT NULL
);
```

## 改动范围

| 模块 | 文件 | 改动 |
|------|------|------|
| common | — | 不变 |
| storage | FileMetadata.java | 加 JPA 注解（`@Entity`, `@Id`, `@Column`） |
| storage | JpaFileMetadataRepository.java | **新增**，替代 InMemory 版本 |
| storage | FileMetadataRepository.java | 接口不变 |
| parent | pom.xml | 加 MySQL connector 版本管理 |
| storage | pom.xml | 加 spring-boot-starter-data-jpa + mysql-connector-j |
| api | application.yml | 加 spring.datasource + spring.jpa 配置 |
| api | StorageController | 调整 initUpload 的去重 + 覆盖逻辑 |
