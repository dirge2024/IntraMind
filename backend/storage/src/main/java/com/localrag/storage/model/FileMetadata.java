package com.localrag.storage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_metadata")
public class FileMetadata {

    public enum Status {
        READY,
        DELETED
    }

    @Id
    @Column(name = "md5", length = 32)
    private String md5;

    @Column(name = "file_name", length = 500, nullable = false)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "object_key", length = 600, nullable = false)
    private String objectKey;

    @Column(name = "bucket", length = 100, nullable = false)
    private String bucket;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
