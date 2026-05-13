package com.localrag.agent.model;

public record ReferenceInfo(
        String fileMd5,
        String fileName,
        Integer pageNumber,
        String anchorText,
        String retrievalMode,
        Double score,
        Integer chunkId
) {}
