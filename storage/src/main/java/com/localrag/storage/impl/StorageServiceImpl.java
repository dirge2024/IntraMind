package com.localrag.storage.impl;

import com.localrag.storage.exception.StorageException;
import com.localrag.storage.contract.StorageService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;

    @Override
    public String createMultipartUpload(String bucket, String objectKey) {
        log.info("multipart upload initiated: bucket={}, key={}", bucket, objectKey);
        return "";
    }

    @Override
    public String uploadPart(String bucket, String objectKey, String uploadId,
                             int partNumber, InputStream stream, long size) {
        try {
            String chunkKey = objectKey + "/chunks/" + String.format("%05d", partNumber);
            ObjectWriteResponse response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(chunkKey)
                            .stream(stream, size, -1)
                            .build());
            return response.etag();
        } catch (Exception e) {
            log.error("uploadPart failed: part={}, bucket={}, key={}", partNumber, bucket, objectKey, e);
            throw new StorageException("分片上传失败");
        }
    }

    @Override
    public String completeMultipartUpload(String bucket, String objectKey,
                                          String uploadId, int totalParts) {
        try {
            java.util.List<ComposeSource> sources = new java.util.ArrayList<>();
            for (int i = 0; i < totalParts; i++) {
                String chunkKey = objectKey + "/chunks/" + String.format("%05d", i + 1);
                sources.add(ComposeSource.builder()
                        .bucket(bucket)
                        .object(chunkKey)
                        .build());
            }

            ObjectWriteResponse response = minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .sources(sources)
                            .build());

            for (int i = 0; i < totalParts; i++) {
                String chunkKey = objectKey + "/chunks/" + String.format("%05d", i + 1);
                try {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder().bucket(bucket).object(chunkKey).build());
                } catch (Exception ignored) {
                    log.warn("failed to delete chunk: {}", chunkKey);
                }
            }

            return response.etag();
        } catch (Exception e) {
            log.error("completeMultipartUpload failed: bucket={}, key={}", bucket, objectKey, e);
            throw new StorageException("合并分片失败");
        }
    }

    @Override
    public void abortMultipartUpload(String bucket, String objectKey, String uploadId) {
        log.info("aborting multipart upload: bucket={}, key={}", bucket, objectKey);
    }

    @Override
    public String putObject(String bucket, String objectKey, InputStream stream, long size, String contentType) {
        try {
            ObjectWriteResponse response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(stream, size, -1)
                            .contentType(contentType)
                            .build());
            return response.etag();
        } catch (Exception e) {
            log.error("putObject failed: bucket={}, key={}", bucket, objectKey, e);
            throw new StorageException("上传文件失败");
        }
    }

    @Override
    public String getPresignedUrl(String bucket, String objectKey, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build());
        } catch (Exception e) {
            log.error("getPresignedUrl failed: bucket={}, key={}", bucket, objectKey, e);
            throw new StorageException("生成下载链接失败");
        }
    }

    @Override
    public void removeObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.error("removeObject failed: bucket={}, key={}", bucket, objectKey, e);
            throw new StorageException("删除文件失败");
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.error("bucketExists check failed: bucket={}", bucket, e);
            return false;
        }
    }

    @Override
    public void createBucket(String bucket) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("bucket created: {}", bucket);
        } catch (Exception e) {
            log.error("createBucket failed: bucket={}", bucket, e);
            throw new StorageException("创建bucket失败");
        }
    }
}
