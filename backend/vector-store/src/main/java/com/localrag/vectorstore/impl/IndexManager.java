/** 启动时自动创建 localrag-chunks 索引，含 denseVector 字段映射。 */
package com.localrag.vectorstore.impl;

import com.localrag.vectorstore.contract.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexManager implements CommandLineRunner {

    private static final String INDEX = "localrag-chunks";

    private final VectorStoreService vectorStoreService;

    @Override
    public void run(String... args) {
        try {
            if (!vectorStoreService.indexExists(INDEX)) {
                vectorStoreService.createIndex(INDEX);
                log.info("ES index '{}' created (dense_vector 2048 + ik_max_word)", INDEX);
            } else {
                log.info("ES index '{}' already exists", INDEX);
            }
        } catch (Exception e) {
            log.warn("ES index init failed (app will continue): {}", e.getMessage());
        }
    }
}
