# MySQL 持久化 — 实现方案

## 1. 依赖

父 POM 加版本管理：
```xml
<mysql-connector.version>8.0.33</mysql-connector.version>
```

storage/pom.xml 加：
```xml
<dependency>org.springframework.boot:spring-boot-starter-data-jpa</dependency>
<dependency>com.mysql:mysql-connector-j</dependency>
```

## 2. FileMetadata 改 JPA Entity

```java
@Entity
@Table(name = "file_metadata")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FileMetadata {
    @Id
    @Column(name = "md5", length = 32)
    private String md5;

    @Column(name = "file_name", length = 500, nullable = false)
    private String fileName;
    // ...其他字段加 @Column
}
```

## 3. JPA Repository

```java
public interface JpaFileMetadataRepository
        extends JpaRepository<FileMetadata, String>, FileMetadataRepository {
    // JPA 自动实现 findByMd5 / save / findAll / delete
}
```

`FileMetadataRepository` 接口保持不变，JPA 的 `findById` 就是 `findByMd5`。

## 4. 删除旧实现

删除 `InMemoryFileMetadataRepository.java`，Spring 自动注入 JPA 实现。

## 5. application.yml 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/localrag?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: wsy2642@
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

`createDatabaseIfNotExist=true` 自动建库，`ddl-auto=update` 自动建表。

## 6. Controller 去重覆盖逻辑

```java
FileMetadata existing = fileMetadataRepository.findByMd5(md5);
if (existing != null && existing.getStatus() == FileMetadata.Status.READY) {
    if (existing.getFileName().equals(request.getFileName())) {
        // 同 MD5 + 同文件名 → 秒传
        return 秒传响应;
    }
    // 同 MD5 + 不同文件名 → 删旧对象，走覆盖上传
    storageService.removeObject(existing.getBucket(), existing.getObjectKey());
    existing.setStatus(FileMetadata.Status.DELETED);
    fileMetadataRepository.save(existing);
}
// 继续新上传流程...
```

## 7. 改动文件清单

```
parent pom.xml                     — 加 mysql-connector version
storage/pom.xml                    — 加 jpa + mysql-connector-j
storage/.../model/FileMetadata.java — 加 @Entity @Table @Id @Column
storage/.../contract/              — 不变
storage/.../impl/
  ├── JpaFileMetadataRepository.java  — 新增
  └── InMemoryFileMetadataRepository.java — 删除
api/.../application.yml            — 加 datasource + jpa
api/.../StorageController.java     — 调整去重覆盖逻辑
```
