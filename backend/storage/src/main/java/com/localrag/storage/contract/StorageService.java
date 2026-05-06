/** MinIO 对象存储服务接口：分片上传(composeObject)、文件直传、下载预签名、删除、Bucket 管理。 */
package com.localrag.storage.contract;

import java.io.InputStream;

public interface StorageService {

    String uploadPart(String bucket, String objectKey, int partNumber, InputStream stream, long size);

    String completeMultipartUpload(String bucket, String objectKey, int totalParts);

    void deleteChunks(String bucket, String objectKey, int totalParts);

    String putObject(String bucket, String objectKey, InputStream stream, long size, String contentType);

    String getPresignedUrl(String bucket, String objectKey, int expirySeconds);

    void removeObject(String bucket, String objectKey);

    boolean bucketExists(String bucket);

    void createBucket(String bucket);
}
