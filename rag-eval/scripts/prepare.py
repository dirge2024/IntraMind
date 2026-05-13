#!/usr/bin/env python3
"""解析数据集 → 自动分类 → 分层采样 → 导出标准化评测文件。

用法: python scripts/prepare.py [dataset_name]
输出: output/passages.jsonl, output/queries.json
"""

import json
import os
import random
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from config import DATASETS, OUTPUT_DIR, RANDOM_SEED
from core.classifier import classify_query


def load_baike_qa(cfg: dict) -> list[dict]:
    txt = os.path.join(cfg["cache_dir"], "chinese_baike_41623.txt")
    if not os.path.exists(txt):
        print(f"  错误: {txt} 不存在，请先运行 download.py")
        return []

    min_len = cfg["min_answer_len"]
    entries = []
    with open(txt, encoding="utf-8") as f:
        lines = f.readlines()
        for i in range(0, len(lines) - 1, 3):
            q = lines[i].strip()
            a = lines[i + 1].strip() if i + 1 < len(lines) else ""
            if q and a and len(a) >= min_len:
                entries.append({"query": q, "answer": a})

    print(f"  加载 {len(lines) // 3} 条 Q&A，{len(entries)} 条答案≥{min_len}字")
    return entries


LOADERS = {"baike_qa": load_baike_qa}


def main() -> None:
    name = sys.argv[1] if len(sys.argv) > 1 else "baike_qa"
    cfg = DATASETS[name]
    print(f"=== 准备评测数据: {cfg['name']} ===\n")
    random.seed(RANDOM_SEED)

    # 加载
    entries = load_baike_qa(cfg)
    if not entries:
        return

    # 分类
    print("\n[分类]")
    by_type = defaultdict(list)
    for e in entries:
        by_type[classify_query(e["query"])].append(e)
    for t, items in sorted(by_type.items()):
        print(f"  {t}: {len(items)} 条")

    # 采样
    targets = cfg["targets"]
    print(f"\n[采样]")
    sampled = []
    for qtype, n in targets.items():
        pool = by_type.get(qtype, [])
        actual = min(n, len(pool))
        sampled.extend(random.sample(pool, actual))
        print(f"  {qtype}: 池 {len(pool)} → 采样 {actual}")
    random.shuffle(sampled)

    # 构建标准化数据
    all_passages = {}
    for i, e in enumerate(sampled):
        pid = f"p{i:04d}"
        all_passages[pid] = {"passage_id": pid, "title": e["query"][:30], "text": e["answer"]}

    export_queries = []
    for i, e in enumerate(sampled):
        pid = f"p{i:04d}"
        qtype = classify_query(e["query"])
        same_type = [f"p{j:04d}" for j, s in enumerate(sampled)
                     if classify_query(s["query"]) == qtype and j != i]
        neg = random.sample(same_type, min(10, len(same_type)))
        export_queries.append({
            "query_id": f"q{i:04d}", "query": e["query"], "type": qtype,
            "positive_pids": [pid], "negative_pids": neg,
        })

    print(f"\n  最终: {len(sampled)} queries, {len(all_passages)} passages")

    # 导出
    with open(OUTPUT_DIR / "passages.jsonl", "w", encoding="utf-8") as f:
        for p in all_passages.values():
            f.write(json.dumps(p, ensure_ascii=False) + "\n")
    with open(OUTPUT_DIR / "queries.json", "w", encoding="utf-8") as f:
        json.dump(export_queries, f, ensure_ascii=False, indent=2)

    print(f"  导出: output/passages.jsonl, output/queries.json")
    print("  完成！")


if __name__ == "__main__":
    main()
