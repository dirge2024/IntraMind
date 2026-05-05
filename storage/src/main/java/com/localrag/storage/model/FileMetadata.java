package com.localrag.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    public enum Status {
        READY,
        DELETED
    }

    private String md5;
    private String fileName;
    private long fileSize;
    private String objectKey;
    private String bucket;
    private String contentType;
    private Status status;
    private LocalDateTime createdAt;
}
