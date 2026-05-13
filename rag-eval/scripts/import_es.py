#!/usr/bin/env python3
"""导入测试数据到 ES 并生成向量。

用法: python scripts/import_es.py
需要: EMBEDDING_API_KEY 环境变量（或 application-local.yml 中配置）
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from config import OUTPUT_DIR
from core.es_client import EsClient


def main() -> None:
    print("=== 导入测试数据到 ES ===\n")

    # 加载 passages
    f = OUTPUT_DIR / "passages.jsonl"
    if not f.exists():
        print(f"  错误: {f} 不存在，请先运行 prepare.py")
        sys.exit(1)

    docs = []
    with open(f, encoding="utf-8") as fh:
        for line in fh:
            p = json.loads(line.strip())
            docs.append({
                "chunkId": p["passage_id"],
                "md5": "eval",
                "text": p["text"],
            })

    print(f"  加载 {len(docs)} 条 passages")
    es = EsClient()
    indexed = es.bulk_index(docs)
    print(f"\n  导入完成: {indexed}/{len(docs)} 条")
    print(f"  索引文档总数: {es.doc_count()}")
    print(f"\n  清理命令: curl -X POST '{es.host}/{es.index}/_delete_by_query'"
          f" -u {es.auth[0]}:**** -H 'Content-Type: application/json'"
          f" -d '{{\"query\":{{\"term\":{{\"md5\":\"eval\"}}}}}}'")


if __name__ == "__main__":
    main()
