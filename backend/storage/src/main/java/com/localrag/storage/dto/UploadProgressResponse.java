package com.localrag.storage.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UploadProgressResponse {
    private String md5;
    private int totalParts;
    private List<Integer> uploadedParts;
    private long uploadedSize;
    private double percentage;
}
