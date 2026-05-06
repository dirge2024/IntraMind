/** MinIO 客户端配置，从 application.yml 读取连接参数，启动时自动创建 Bucket。 */
package com.localrag.storage.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "localrag.minio")
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private int presignedExpirySeconds = 600;
    private int uploadExpireHours = 24;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
