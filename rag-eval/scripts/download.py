#!/usr/bin/env python3
"""下载数据集。支持多个开源数据集，通过 dataset 参数切换。

用法: python scripts/download.py [dataset_name]
  dataset_name: baike_qa (默认) | 后续扩展其他数据集
"""

import os
import shutil
import subprocess
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent.parent))
from config import DATASETS


def download_baike_qa(cfg: dict) -> None:
    repo = cfg["repo"]
    cache = cfg["cache_dir"]
    if os.path.exists(cache):
        shutil.rmtree(cache)
    subprocess.run(["git", "clone", "--depth", "1", repo, cache], check=True)
    txt = os.path.join(cache, "chinese_baike_41623.txt")
    with open(txt, encoding="utf-8") as f:
        count = sum(1 for _ in f) // 3
    print(f"  下载完成: {count} 条 Q&A")


DOWNLOADERS = {
    "baike_qa": download_baike_qa,
}


def main() -> None:
    name = sys.argv[1] if len(sys.argv) > 1 else "baike_qa"
    if name not in DATASETS:
        print(f"未知数据集: {name}。可用: {list(DATASETS)}")
        sys.exit(1)

    cfg = DATASETS[name]
    print(f"=== 下载数据集: {cfg['name']} ===")
    DOWNLOADERS[name](cfg)


if __name__ == "__main__":
    main()
