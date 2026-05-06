/** 检索 API：POST /api/retrieval/search，返回 TopK 相关 chunk 及相似度分数。 */
package com.localrag.controller;

import com.localrag.common.Result;
import com.localrag.retrieval.contract.RetrievalService;
import com.localrag.retrieval.model.RetrievalResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/retrieval")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    @PostMapping("/search")
    public Result<List<RetrievalResult>> search(@RequestBody SearchRequest request) {
        List<RetrievalResult> results = retrievalService.search(request.getQuery(), request.getTopK());
        log.info("retrieval: query='{}', results={}", request.getQuery(), results.size());
        return Result.ok(results);
    }

    @Data
    public static class SearchRequest {
        private String query;
        private int topK = 5;
    }
}
