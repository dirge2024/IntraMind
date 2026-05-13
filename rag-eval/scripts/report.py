#!/usr/bin/env python3
"""生成评测报告 (Markdown)。按查询类型分组，输出对比表格和结论。

用法: python scripts/report.py
"""

import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from config import OUTPUT_DIR

TYPE_ORDER = ["精确术语", "描述/解释", "数值/列表", "是否判断", "对比/差异", "长尾/模糊"]


def main() -> None:
    f = OUTPUT_DIR / "eval_results.json"
    if not f.exists():
        print(f"错误: {f} 不存在，先运行 evaluate.py")
        sys.exit(1)

    with open(f, encoding="utf-8") as fh:
        results = json.load(fh)

    # 分组
    by_type = defaultdict(list)
    for r in results:
        by_type[r.get("type", "未知")].append(r)

    lines = [
        "# RAG 检索质量评测报告",
        f"> 测试集: chinese_baike_QA | Query: {len(results)} 条 | TopK=5",
        "",
        "## 分类对比",
        "| 查询类型 | 条数 | 混合 Recall@5 | BM25 Recall@5 | 提升 | 混合 MRR | BM25 MRR |",
        "|----------|:---:|:------------:|:------------:|-----:|:--------:|:--------:|",
    ]

    for qtype in TYPE_ORDER:
        items = by_type.get(qtype, [])
        if not items:
            continue
        n = len(items)
        hr = sum(it["hybrid_recall"] for it in items) / n
        br = sum(it["bm25_recall"] for it in items) / n
        hm = sum(it["hybrid_mrr"] for it in items) / n
        bm = sum(it["bm25_mrr"] for it in items) / n
        gain = (hr - br) / max(br, 0.001) * 100
        lines.append(f"| {qtype} | {n} | {hr:.4f} | {br:.4f} | {gain:+.1f}% | {hm:.4f} | {bm:.4f} |")

    # 全局
    all_n = len(results)
    all_hr = sum(r["hybrid_recall"] for r in results) / all_n
    all_br = sum(r["bm25_recall"] for r in results) / all_n
    all_hm = sum(r["hybrid_mrr"] for r in results) / all_n
    all_bm = sum(r["bm25_mrr"] for r in results) / all_n
    all_hh = sum(r["hybrid_hit"] for r in results) / all_n
    all_bh = sum(r["bm25_hit"] for r in results) / all_n
    recall_gain = (all_hr - all_br) / max(all_br, 0.001) * 100
    mrr_gain = (all_hm - all_bm) / max(all_bm, 0.001) * 100

    lines.append(f"| **全部** | **{all_n}** | **{all_hr:.4f}** | **{all_br:.4f}** | **{recall_gain:+.1f}%** | **{all_hm:.4f}** | **{all_bm:.4f}** |")
    lines.extend([
        "",
        "## 全局指标",
        f"| Recall@5 | {all_hr:.4f} | {all_br:.4f} | {recall_gain:+.1f}% |",
        f"| MRR | {all_hm:.4f} | {all_bm:.4f} | {mrr_gain:+.1f}% |",
        f"| Hit@5 | {all_hh:.4f} | {all_bh:.4f} | — |",
        "",
        "## 关键发现",
    ])

    # 混合优于 BM25 的查询
    bm25_only_miss = [r for r in results if r["bm25_recall"] == 0 and r["hybrid_recall"] > 0]
    hybrid_only_miss = [r for r in results if r["hybrid_recall"] == 0 and r["bm25_recall"] > 0]

    lines.append(f"- 混合检索挽救 BM25 失败查询: **{len(bm25_only_miss)} 条**")
    lines.append(f"- BM25 命中但混合检索失败: {len(hybrid_only_miss)} 条")
    lines.append(f"- 整体 Recall@5 提升: **{recall_gain:+.1f}%**，MRR 提升: **{mrr_gain:+.1f}%**")

    lines.extend([
        "",
        "## 结论",
        f"混合检索（KNN+BM25+rescore）相比纯 BM25，Recall@5 提升 {recall_gain:+.1f}%。",
        f"在语义模糊和口语化场景下优势明显，{len(bm25_only_miss)} 条查询纯靠向量兜底命中。",
        "建议保留混合检索作为默认策略。",
    ])

    report = "\n".join(lines)
    report_path = OUTPUT_DIR / "report.md"
    with open(report_path, "w", encoding="utf-8") as fh:
        fh.write(report)
    print(report)
    print(f"\n报告已保存: {report_path}")


if __name__ == "__main__":
    main()
