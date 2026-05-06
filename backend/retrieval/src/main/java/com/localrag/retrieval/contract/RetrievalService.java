package com.localrag.retrieval.contract;

import com.localrag.retrieval.model.RetrievalResult;

import java.util.List;

public interface RetrievalService {
    List<RetrievalResult> search(String query, int topK);
}
