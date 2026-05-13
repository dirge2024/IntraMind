#!/usr/bin/env python3
"""核心评测: 混合检索 vs 纯 BM25，计算 Recall@5 / MRR / Hit@5。

用法: python scripts/evaluate.py
前提: 后端运行中，ES 运行中，测试数据已入库
"""

import json
import sys
import time
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from config import OUTPUT_DIR, TOPK
from core.metrics import calc_recall_at_k, calc_mrr, calc_hit_at_k
from core.search import search_hybrid, search_bm25


def main() -> None:
    print(f"=== RAG 检索评测 (TopK={TOPK}) ===\n")

    f = OUTPUT_DIR / "queries.json"
    if not f.exists():
        print(f"  错误: {f} 不存在")
        sys.exit(1)

    with open(f, encoding="utf-8") as fh:
        queries = json.load(fh)
    print(f"  加载 {len(queries)} 条查询")

    results = []
    hybrid_fails = bm25_fails = 0
    t0 = time.time()

    for i, q in enumerate(queries):
        qid = q["query_id"]
        positives = q["positive_pids"]
        qtype = q.get("type", "未知")

        h_ids = search_hybrid(q["query"])
        b_ids = search_bm25(q["query"])

        if not h_ids:
            hybrid_fails += 1
        if not b_ids:
            bm25_fails += 1

        results.append({
            "query_id": qid, "query": q["query"], "type": qtype,
            "positive_pids": positives,
            "hybrid_recall": round(calc_recall_at_k(h_ids, positives), 4),
            "hybrid_mrr": round(calc_mrr(h_ids, positives), 4),
            "hybrid_hit": calc_hit_at_k(h_ids, positives),
            "bm25_recall": round(calc_recall_at_k(b_ids, positives), 4),
            "bm25_mrr": round(calc_mrr(b_ids, positives), 4),
            "bm25_hit": calc_hit_at_k(b_ids, positives),
        })

        if (i + 1) % 50 == 0:
            print(f"  [{i + 1}/{len(queries)}]")

    elapsed = time.time() - t0
    print(f"  完成 ({elapsed:.1f}s), 混合检索失败 {hybrid_fails}, BM25 失败 {bm25_fails}")

    # 保存
    with open(OUTPUT_DIR / "eval_results.json", "w", encoding="utf-8") as fh:
        json.dump(results, fh, ensure_ascii=False, indent=2)

    # 快速汇总
    valid = [r for r in results if r["hybrid_recall"] >= 0 and r["bm25_recall"] >= 0]
    if valid:
        hr = sum(r["hybrid_recall"] for r in valid) / len(valid)
        br = sum(r["bm25_recall"] for r in valid) / len(valid)
        hm = sum(r["hybrid_mrr"] for r in valid) / len(valid)
        bm = sum(r["bm25_mrr"] for r in valid) / len(valid)
        print(f"\n  Recall@5:  混合 {hr:.4f}  BM25 {br:.4f}  +{(hr - br) / max(br, 0.001) * 100:.1f}%")
        print(f"  MRR:       混合 {hm:.4f}  BM25 {bm:.4f}  +{(hm - bm) / max(bm, 0.001) * 100:.1f}%")
    print(f"\n  运行 scripts/report.py 生成详细报告")


if __name__ == "__main__":
    main()
