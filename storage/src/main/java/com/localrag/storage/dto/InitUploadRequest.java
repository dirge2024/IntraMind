package com.localrag.storage.dto;

import lombok.Data;

@Data
public class InitUploadRequest {
    private String fileMd5;
    private String fileName;
    private long fileSize;
}
