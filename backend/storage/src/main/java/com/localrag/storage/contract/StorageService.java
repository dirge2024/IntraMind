package com.localrag.storage.contract;

import java.io.InputStream;

public interface StorageService {

    String createMultipartUpload(String bucket, String objectKey);

    String uploadPart(String bucket, String objectKey, String uploadId, int partNumber, InputStream stream, long size);

    String completeMultipartUpload(String bucket, String objectKey, String uploadId, int totalParts);

    void abortMultipartUpload(String bucket, String objectKey, String uploadId);

    String putObject(String bucket, String objectKey, InputStream stream, long size, String contentType);

    String getPresignedUrl(String bucket, String objectKey, int expirySeconds);

    void removeObject(String bucket, String objectKey);

    boolean bucketExists(String bucket);

    void createBucket(String bucket);
}
