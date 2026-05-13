"""ES 客户端 — 封装连接和基础操作。"""

import json
import sys
import time
from typing import Any

import requests

from config import ES_HOST, ES_AUTH, ES_INDEX, EMBEDDING_KEY, EMBEDDING_ENDPOINT, EMBEDDING_MODEL, EMBEDDING_DIM, EMBEDDING_BATCH


class EsClient:
    """Elasticsearch 操作封装。"""

    def __init__(self, host: str = ES_HOST, auth: tuple = ES_AUTH, index: str = ES_INDEX):
        self.host = host
        self.auth = auth
        self.index = index

    # ---- 基础操作 ----
    def _req(self, method: str, path: str, **kwargs) -> requests.Response:
        url = f"{self.host}{path}"
        kwargs.setdefault("auth", self.auth)
        kwargs.setdefault("timeout", 30)
        return requests.request(method, url, **kwargs)

    def health(self) -> bool:
        try:
            r = self._req("GET", "/_cluster/health", timeout=5)
            return r.status_code == 200
        except Exception:
            return False

    def doc_count(self) -> int:
        try:
            r = self._req("GET", f"/{self.index}/_count")
            return r.json().get("count", 0)
        except Exception:
            return 0

    def delete_by_md5(self, md5: str) -> int:
        r = self._req("POST", f"/{self.index}/_delete_by_query",
                       json={"query": {"term": {"md5": md5}}})
        return r.json().get("deleted", 0)

    # ---- 批量导入 ----
    def bulk_index(self, docs: list[dict]) -> int:
        """批量导入文档（含 embedding 向量化）。返回成功条数。"""
        total = len(docs)
        indexed = 0

        for i in range(0, total, EMBEDDING_BATCH):
            batch = docs[i: i + EMBEDDING_BATCH]
            texts = [d.get("text", "")[:8000] for d in batch]
            vectors = self._embed(texts)

            lines = []
            for j, doc in enumerate(batch):
                action = json.dumps({"index": {"_index": self.index, "_id": doc["chunkId"]}})
                body = json.dumps({
                    "chunkId": doc["chunkId"],
                    "md5": doc.get("md5", "eval"),
                    "text": texts[j],
                    "charCount": len(texts[j]),
                    "denseVector": vectors[j] if j < len(vectors) else [0.0] * EMBEDDING_DIM,
                }, ensure_ascii=False)
                lines.extend([action, body])

            r = self._req("POST", "/_bulk",
                          data=("\n".join(lines) + "\n").encode("utf-8"),
                          headers={"Content-Type": "application/x-ndjson"},
                          timeout=60)
            result = r.json()
            ok = sum(1 for it in result.get("items", []) if it.get("index", {}).get("status") in (200, 201))
            indexed += ok
            if i % 50 == 0:
                print(f"  [{min(i + EMBEDDING_BATCH, total)}/{total}] {indexed * 100 // total}%")

        self._req("POST", f"/{self.index}/_refresh")
        return indexed

    # ---- 检索 ----
    def search_bm25(self, query: str, top_k: int = 5) -> list[str]:
        """纯 BM25 检索，返回 chunkId 列表。"""
        try:
            r = self._req("POST", f"/{self.index}/_search", json={
                "query": {"match": {"text": query}},
                "size": top_k,
            })
            if r.status_code != 200:
                return []
            return [h["_source"].get("chunkId", "") for h in r.json()["hits"]["hits"]]
        except Exception:
            return []

    # ---- Embedding ----
    def _embed(self, texts: list[str]) -> list[list[float]]:
        if not EMBEDDING_KEY or not any(texts):
            return [[0.0] * EMBEDDING_DIM] * len(texts)

        payload = {
            "model": EMBEDDING_MODEL,
            "input": {"texts": texts},
            "parameters": {"dimension": EMBEDDING_DIM},
        }
        headers = {"Authorization": f"Bearer {EMBEDDING_KEY}", "Content-Type": "application/json"}

        for attempt in range(3):
            try:
                r = requests.post(EMBEDDING_ENDPOINT, json=payload, headers=headers, timeout=30)
                if r.status_code == 200:
                    return [e["embedding"] for e in r.json()["output"]["embeddings"]]
            except Exception:
                pass
            if attempt < 2:
                time.sleep(2 ** attempt)

        print(f"  Embedding 失败，使用零向量占位")
        return [[0.0] * EMBEDDING_DIM] * len(texts)
