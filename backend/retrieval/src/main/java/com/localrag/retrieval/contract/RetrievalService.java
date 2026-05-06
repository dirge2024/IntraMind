/** 检索服务接口：search(query,topK) → List<RetrievalResult>。三级瀑布：KNN→BM25→rescore。 */
package com.localrag.retrieval.contract;

import com.localrag.retrieval.model.RetrievalResult;

import java.util.List;

public interface RetrievalService {
    List<RetrievalResult> search(String query, int topK);
}
