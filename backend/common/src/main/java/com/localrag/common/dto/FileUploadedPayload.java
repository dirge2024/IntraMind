package com.localrag.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadedPayload {
    private String md5;
    private String fileName;
    private long fileSize;
    private String objectKey;
}
