package com.localrag.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTask {

    public enum Status {
        UPLOADING
    }

    private String md5;
    private String fileName;
    private long fileSize;
    private String uploadId;
    private String bucket;
    private String objectKey;
    private int totalParts;
    private int partSize;
    private Status status;
    private LocalDateTime createdAt;

    @Builder.Default
    private Map<Integer, String> uploadedParts = new ConcurrentHashMap<>();
}
