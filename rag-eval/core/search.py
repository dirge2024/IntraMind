"""检索封装: 混合检索(IntraMind API) + 纯 BM25(ES 直连)。"""

import requests

from config import API_URL, ES_HOST, ES_INDEX, ES_AUTH, TOPK


def search_hybrid(query: str, top_k: int = TOPK) -> list[str]:
    """通过 IntraMind API 混合检索，返回 chunkId 列表。"""
    try:
        resp = requests.post(
            f"{API_URL}/api/retrieval/search",
            json={"query": query, "topK": top_k},
            timeout=30,
        )
        if resp.status_code == 401:
            resp = requests.post(
                f"{API_URL}/api/retrieval/search",
                json={"query": query, "topK": top_k},
                headers={"Authorization": "Bearer anonymous"},
                timeout=30,
            )
        if resp.status_code != 200:
            return []
        return [str(r.get("chunkId", "")) for r in resp.json().get("data", [])]
    except Exception:
        return []


def search_bm25(query: str, top_k: int = TOPK) -> list[str]:
    """纯 BM25 检索 (ES match 查询)，返回 chunkId 列表。"""
    try:
        resp = requests.post(
            f"{ES_HOST}/_search",
            json={"query": {"match": {"text": query}}, "size": top_k},
            auth=ES_AUTH,
            timeout=10,
        )
        if resp.status_code != 200:
            return []
        return [h["_source"].get("chunkId", "") for h in resp.json()["hits"]["hits"]]
    except Exception:
        return []
