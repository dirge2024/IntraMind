package com.localrag.storage.impl;

import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaFileMetadataRepository
        extends JpaRepository<FileMetadata, String>, FileMetadataRepository {

    @Override
    default FileMetadata findByMd5(String md5) {
        return findById(md5).orElse(null);
    }

    @Override
    default void delete(String md5) {
        deleteById(md5);
    }
}
