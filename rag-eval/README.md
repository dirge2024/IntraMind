# RAG 检索质量评测

对比 IntraMind 混合检索（KNN+BM25+rescore）vs 纯 BM25 的召回率提升。

## 项目结构

```
rag-eval/
├── config.py           # 全局配置（ES/API/数据集/评测参数）
├── core/               # 核心模块
│   ├── classifier.py   #   查询类型自动分类
│   ├── metrics.py      #   评测指标 (Recall@K, MRR, Hit@K)
│   ├── es_client.py    #   ES 客户端 (连接/导入/embedding)
│   └── search.py       #   检索封装 (混合API + BM25直连)
├── scripts/            # 流水线脚本
│   ├── download.py     #   下载数据集
│   ├── prepare.py      #   解析 → 分类 → 采样 → 标准化导出
│   ├── import_es.py    #   导入 ES + 向量化
│   ├── evaluate.py     #   跑评测（混合 vs BM25）
│   └── report.py       #   生成 Markdown 报告
├── data/               # 原始数据集（gitignored）
├── output/             # 评测产出（passages/queries/results/report）
└── docs/               # 需求分析 + 技术方案
```

## 快速开始

```bash
# 1. 下载数据集
python scripts/download.py baike_qa

# 2. 准备评测数据
python scripts/prepare.py baike_qa

# 3. 导入 ES（需设置 EMBEDDING_API_KEY）
export EMBEDDING_API_KEY=your_key
python scripts/import_es.py

# 4. 跑评测（后端 + ES 需运行中）
python scripts/evaluate.py

# 5. 生成报告
python scripts/report.py
```

## 扩展新数据集

1. 在 `config.py` 的 `DATASETS` 中注册
2. 在 `scripts/download.py` 添加下载函数
3. 在 `scripts/prepare.py` 添加解析函数
