package com.localrag.storage.contract;

import com.localrag.storage.model.FileMetadata;

import java.util.List;

public interface FileMetadataRepository {

    FileMetadata save(FileMetadata metadata);

    FileMetadata findByMd5(String md5);

    List<FileMetadata> findAll();

    void delete(String md5);
}
