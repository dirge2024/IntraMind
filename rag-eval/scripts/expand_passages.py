#!/usr/bin/env python3
"""用 DeepSeek 将 baike_QA 短答案扩写成 200-400 字百科式长段落。

用法: python scripts/expand_passages.py
需要: DEEPSEEK_API_KEY 环境变量
"""

import json
import os
import sys
import time
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from config import OUTPUT_DIR

DEEPSEEK_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
BATCH_SIZE = 10


def expand(text: str, query: str) -> str:
    """调用 DeepSeek 将短答案扩写成百科式段落。"""
    prompt = (
        f"请基于以下问答对，生成一段200-400字的百科式段落，"
        f"内容自然流畅，不要标注来源。\n"
        f"问题：{query}\n答案：{text}\n\n段落："
    )
    payload = {
        "model": "deepseek-chat",
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": 500,
        "temperature": 0.7,
        "stream": False,
    }
    headers = {"Authorization": f"Bearer {DEEPSEEK_KEY}",
               "Content-Type": "application/json"}

    for attempt in range(3):
        try:
            r = requests.post(DEEPSEEK_URL, json=payload, headers=headers, timeout=60)
            if r.status_code == 200:
                return r.json()["choices"][0]["message"]["content"].strip()
        except Exception:
            pass
        if attempt < 2:
            time.sleep(2)
    return text  # 失败则保留原文


def main() -> None:
    queries_file = OUTPUT_DIR / "queries.json"
    passages_file = OUTPUT_DIR / "passages.jsonl"

    if not DEEPSEEK_KEY:
        # Try from application-local.yml
        local_yml = Path(__file__).resolve().parent.parent.parent / \
                    "backend" / "api" / "src" / "main" / "resources" / "application-local.yml"
        if local_yml.exists():
            import re
            with open(local_yml) as f:
                for line in f:
                    m = re.search(r'api-key:\s*(\S+)', line)
                    if m:
                        os.environ["DEEPSEEK_API_KEY"] = m.group(1)
        if not os.environ.get("DEEPSEEK_API_KEY"):
            print("  错误: 需要 DEEPSEEK_API_KEY 环境变量")
            sys.exit(1)

    # Try reading from application-local
    print("=== 扩写 passages ===")
    print(f"  读取: {passages_file}")

    if not passages_file.exists():
        print(f"  错误: {passages_file} 不存在")
        sys.exit(1)

    items = []
    with open(passages_file, encoding="utf-8") as f:
        for line in f:
            items.append(json.loads(line.strip()))

    total = len(items)
    print(f"  共 {total} 条 passages")

    for i in range(0, total, BATCH_SIZE):
        batch = items[i: i + BATCH_SIZE]
        for item in batch:
            expanded = expand(item["text"], item.get("title", ""))
            item["text"] = expanded
        print(f"  [{min(i + BATCH_SIZE, total)}/{total}]", flush=True)
        time.sleep(0.5)  # 避免触发频率限制

    # 写回
    with open(passages_file, "w", encoding="utf-8") as f:
        for item in items:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    # 统计
    lens = [len(it["text"]) for it in items]
    print(f"\n  完成！passage: min={min(lens)}, max={max(lens)}, avg={sum(lens)//len(lens)} 字")


if __name__ == "__main__":
    main()
