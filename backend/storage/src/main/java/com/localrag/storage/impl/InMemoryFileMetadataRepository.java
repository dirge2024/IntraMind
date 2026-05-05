package com.localrag.storage.impl;

import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.model.FileMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryFileMetadataRepository implements FileMetadataRepository {

    private final ConcurrentHashMap<String, FileMetadata> store = new ConcurrentHashMap<>();

    @Override
    public FileMetadata save(FileMetadata metadata) {
        store.put(metadata.getMd5(), metadata);
        return metadata;
    }

    @Override
    public FileMetadata findByMd5(String md5) {
        return store.get(md5);
    }

    @Override
    public List<FileMetadata> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(String md5) {
        store.remove(md5);
    }
}
