package com.localrag.storage.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadPartResponse {
    private int partNumber;
    private String etag;
    private int uploadedCount;
    private int totalParts;
}
