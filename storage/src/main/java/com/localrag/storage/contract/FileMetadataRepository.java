package com.localrag.storage.contract;

import com.localrag.storage.model.FileMetadata;

public interface FileMetadataRepository {

    FileMetadata save(FileMetadata metadata);

    FileMetadata findByMd5(String md5);

    void delete(String md5);
}
