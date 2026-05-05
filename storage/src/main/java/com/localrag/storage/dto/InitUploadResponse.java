package com.localrag.storage.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InitUploadResponse {
    private String md5;
    private String uploadId;
    private int partSize;
    private int totalParts;
    private List<Integer> uploadedParts;
    private String status; // NEW / RESUME / EXIST
    private String fileName;
    private long fileSize;
    private String url;
}
