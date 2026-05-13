#!/usr/bin/env python3
"""用 DeepSeek 批量生成评测查询并标注正例 chunk。

用法: python scripts/generate_queries.py
输出: output/queries.json (完整标注的评测集)
"""

import json
import os
import random
import re
import sys
import time
from collections import defaultdict
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from config import OUTPUT_DIR

DEEPSEEK_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
ES_URL = "http://localhost:9200"
ES_AUTH = ("elastic", "wsy2642@")
RANDOM_SEED = 42

# 目标分配
TARGETS = {
    "精确术语": 100,
    "描述/解释": 120,
    "数值/列表": 70,
    "是否判断": 60,
    "对比/差异": 70,
    "长尾/模糊": 50,
    "知识库不存在": 30,
}

# 知识库不存在的领域（确保不在已有文档中）
NEGATIVE_TOPICS = [
    "MongoDB 副本集怎么搭建",
    "React Hooks 有哪些",
    "Vue3 和 React 的区别",
    "Python 的 GIL 是什么",
    "Golang 的 goroutine 调度原理",
    "Redis Cluster 和 Sentinel 的区别",
    "MySQL 分库分表中间件有哪些",
    "Linux 内核进程调度算法",
    "TCP 三次握手和四次挥手的过程",
    "HTTPS 证书验证流程是什么",
    "微服务网关 Zuul 和 Gateway 的区别",
    "Hadoop 和 Spark 的对比",
    "消息队列 Kafka 和 RocketMQ 的区别",
    "分布式事务的 Seata 方案",
    "Nginx 反向代理和负载均衡配置",
    "Git 的 rebase 和 merge 区别",
    "JVM 的 CMS 和 G1 垃圾回收器对比",
    "雪花算法生成分布式 ID 的原理",
    "OAuth2.0 授权码模式流程",
    "设计模式中工厂模式和策略模式的区别",
    "为什么 Python 比 Java 慢",
    "什么是 WebAssembly",
    "GraphQL 和 RESTful 的区别",
    "怎么用 Prometheus 监控 Spring Boot 应用",
    "Redis 集群模式下 slot 迁移过程",
    "Elasticsearch 的 refresh、flush、merge 区别",
    "Java 中 volatile 和 synchronized 的区别",
    "HTTP/2 的多路复用原理",
    "什么是零拷贝技术",
    "怎么排查 Java 内存泄漏",
]


def call_deepseek(prompt: str) -> str:
    headers = {"Authorization": f"Bearer {DEEPSEEK_KEY}", "Content-Type": "application/json"}
    payload = {"model": "deepseek-chat", "messages": [{"role": "user", "content": prompt}],
               "max_tokens": 4000, "temperature": 0.7, "stream": False}
    for attempt in range(3):
        try:
            r = requests.post(DEEPSEEK_URL, json=payload, headers=headers, timeout=120)
            if r.status_code == 200:
                return r.json()["choices"][0]["message"]["content"]
        except Exception:
            pass
        if attempt < 2:
            time.sleep(3)
    return ""


def load_chunks() -> dict:
    """从 ES 加载所有非 eval chunk，按文档分组。"""
    resp = requests.post(f"{ES_URL}/localrag-chunks/_search", auth=ES_AUTH,
                         json={"query": {"bool": {"must_not": [{"term": {"md5": "eval"}}]}},
                               "size": 2000, "_source": ["chunkId", "md5", "text"]}, timeout=30)
    hits = resp.json()["hits"]["hits"]

    # 文件名映射
    r2 = requests.get("http://localhost:8088/api/storage/files").json()
    md5_to_name = {f["md5"]: f["fileName"] for f in r2.get("data", [])}

    docs = defaultdict(list)
    for h in hits:
        s = h["_source"]
        name = md5_to_name.get(s["md5"], s["md5"][:8])
        docs[name].append({"cid": s["chunkId"], "text": s["text"].replace("\n", " ")})
    return dict(docs)


def generate_single_doc_queries(doc_name: str, chunks: list, qtype: str, count: int) -> list:
    """为单个文档生成指定类型和数量的查询。"""
    random.shuffle(chunks)
    sample = chunks[:min(count * 2, len(chunks))]

    # 构建 prompt
    lines = "\n".join([f"[{c['cid']}] {c['text'][:300]}" for c in sample[:30]])
    prompt = f"""你是评测数据集构建专家。请基于以下文档片段，生成{count}条中文查询。

文档: {doc_name}
查询类型: {qtype}
要求:
- {"查询答案可直接在片段中找到，使用精确的术语、命令名、版本号等" if qtype == "精确术语" else ""}
- {"查询问'是什么''如何工作''什么原理'" if qtype == "描述/解释" else ""}
- {"查询问'有哪些''几个''多少'" if qtype == "数值/列表" else ""}
- {"查询问'能不能''会不会''是不是'" if qtype == "是否判断" else ""}
- {"查询口语化、简短、用近义词替代原文关键词" if qtype == "长尾/模糊" else ""}
- 每条查询标注对应的 chunk_id（答案所在的 chunk）
- 每条查询 1-3 个正例

输出 JSON 数组:
[{{"query": "...", "positive_pids": ["chunk_id1"]}}, ...]

文档片段:
{lines}

只输出 JSON 数组，不要其他文字。"""

    resp = call_deepseek(prompt)
    try:
        # 尝试提取 JSON
        match = re.search(r'\[.*\]', resp, re.DOTALL)
        if match:
            return json.loads(match.group())
    except Exception:
        pass
    return []


def generate_cross_doc_queries(docs: dict, count: int) -> list:
    """生成跨文档对比查询。"""
    doc_pairs = [("Redis设计与实现.pdf", "MySQL 8从入门到精通（视频教学版）.pdf"),
                 ("Elasticsearch 权威指南（中文版）高清PDF.pdf", "MySQL 8从入门到精通（视频教学版）.pdf"),
                 ("Docker - 从入门到实践.pdf", "SpringBoot实战（自带目录）第四版.pdf"),
                 ("Redis设计与实现.pdf", "Elasticsearch 权威指南（中文版）高清PDF.pdf"),
                 ("Java 八股.pdf", "SpringBoot实战（自带目录）第四版.pdf"),
                 ("Docker - 从入门到实践.pdf", "Redis设计与实现.pdf")]

    results = []
    for a, b in doc_pairs[:4]:
        chunks_a = docs.get(a, [])[:5]
        chunks_b = docs.get(b, [])[:5]
        if not chunks_a or not chunks_b:
            continue

        prompt = f"""生成{count//4}条跨文档对比查询。要求查询同时涉及两篇文档的内容。

文档A: {a}
文档B: {b}
查询类型: 对比/差异

输出 JSON:
[{{"query": "...", "positive_pids": ["a_chunk_id", "b_chunk_id"]}}]

只输出 JSON 数组。"""
        resp = call_deepseek(prompt)
        try:
            match = re.search(r'\[.*\]', resp, re.DOTALL)
            if match:
                results.extend(json.loads(match.group()))
        except Exception:
            pass
    return results[:count]


def main() -> None:
    print("=== 生成评测查询 ===\n")
    random.seed(RANDOM_SEED)
    docs = load_chunks()
    print(f"  加载 {len(docs)} 份文档, {sum(len(v) for v in docs.values())} chunks\n")

    all_queries = []

    # 1. 单文档查询（精确术语/描述解释/数值列表/是否判断/长尾模糊）
    single_types = ["精确术语", "描述/解释", "数值/列表", "是否判断", "长尾/模糊"]
    for qtype in single_types:
        total = TARGETS[qtype]
        per_doc = max(5, total // len(docs))
        print(f"[{qtype}] 目标 {total} 条...")

        for doc_name, chunks in sorted(docs.items()):
            if len(chunks) < 20:
                continue
            qs = generate_single_doc_queries(doc_name, chunks, qtype, per_doc)
            for q in qs:
                q["type"] = qtype
            all_queries.extend(qs)
            print(f"  {doc_name[:40]}: {len(qs)} 条")
        time.sleep(2)

    # 2. 跨文档对比查询
    print(f"\n[对比/差异] 目标 {TARGETS['对比/差异']} 条...")
    cross = generate_cross_doc_queries(docs, TARGETS["对比/差异"])
    for q in cross:
        q["type"] = "对比/差异"
    all_queries.extend(cross)
    print(f"  生成 {len(cross)} 条")

    # 3. 知识库不存在
    print(f"\n[知识库不存在] 目标 {TARGETS['知识库不存在']} 条...")
    neg_count = TARGETS["知识库不存在"]
    for i, topic in enumerate(NEGATIVE_TOPICS[:neg_count]):
        all_queries.append({
            "query": topic, "type": "知识库不存在",
            "positive_pids": [], "negative_pids": [],
        })
    print(f"  生成 {neg_count} 条")

    # 4. 数量不足时补齐
    print(f"\n  总计生成 {len(all_queries)} 条查询")

    # 重新编号
    for i, q in enumerate(all_queries):
        q["query_id"] = f"q{i:04d}"

    # 保存
    with open(OUTPUT_DIR / "queries.json", "w", encoding="utf-8") as f:
        json.dump(all_queries, f, ensure_ascii=False, indent=2)

    # 统计
    from collections import Counter
    counts = Counter(q.get("type", "") for q in all_queries)
    for t, n in sorted(counts.items()):
        print(f"  {t}: {n}")

    print(f"\n  完成！→ output/queries.json")


if __name__ == "__main__":
    main()
