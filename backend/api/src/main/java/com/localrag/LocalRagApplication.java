package com.localrag;

import com.localrag.storage.config.MinioConfig;
import com.localrag.storage.contract.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class LocalRagApplication {

    private final MinioConfig minioConfig;
    private final StorageService storageService;

    public static void main(String[] args) {
        SpringApplication.run(LocalRagApplication.class, args);
    }

    @Bean
    CommandLineRunner initBucket() {
        return args -> {
            String bucket = minioConfig.getBucket();
            if (!storageService.bucketExists(bucket)) {
                storageService.createBucket(bucket);
                log.info("Bucket '{}' created on startup", bucket);
            } else {
                log.info("Bucket '{}' already exists", bucket);
            }
        };
    }
}
