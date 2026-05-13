"""全局配置 — 修改此处即可切换数据集和参数。"""

import os
from pathlib import Path

# ---- 路径 ----
ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
OUTPUT_DIR = ROOT / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# ---- ES ----
ES_HOST = os.environ.get("ES_URL", "http://localhost:9200")
ES_USER = os.environ.get("ES_USER", "elastic")
ES_PASS = os.environ.get("ES_PASS", "wsy2642@")
ES_AUTH = (ES_USER, ES_PASS)
ES_INDEX = "localrag-chunks"

# ---- 后端 API ----
API_URL = os.environ.get("API_URL", "http://localhost:8088")

# ---- Embedding ----
EMBEDDING_KEY = os.environ.get("EMBEDDING_API_KEY", "")
EMBEDDING_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
EMBEDDING_MODEL = "text-embedding-v4"
EMBEDDING_DIM = 2048
EMBEDDING_BATCH = 10

# ---- 评测参数 ----
TOPK = 5
RANDOM_SEED = 42

# ---- 数据集注册表 ----
# 新增数据集: 在 DATASETS 中添加条目，实现 download_xxx() 和 prepare_xxx()
DATASETS = {
    "baike_qa": {
        "name": "chinese_baike_QA",
        "repo": "https://github.com/YangxiuLiu/chinese_baike_QA.git",
        "cache_dir": str(DATA_DIR / "baike_qa"),
        "targets": {
            "精确术语": 120, "描述/解释": 90, "数值/列表": 90,
            "是否判断": 100, "对比/差异": 27, "长尾/模糊": 70,
        },
        "min_answer_len": 10,
    },
}
