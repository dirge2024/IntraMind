"""检索评测指标: Recall@K, MRR, Hit@K。"""

from typing import List


def calc_recall_at_k(returned_ids: List[str], positive_ids: List[str], k: int = 5) -> float:
    """前 K 个结果中命中正确答案的比例。"""
    top_k = set(returned_ids[:k])
    positives = set(positive_ids)
    if not positives:
        return 0.0
    return len(top_k & positives) / len(positives)


def calc_mrr(returned_ids: List[str], positive_ids: List[str]) -> float:
    """第一个正确答案排名的倒数均值。"""
    positives = set(positive_ids)
    for i, cid in enumerate(returned_ids):
        if cid in positives:
            return 1.0 / (i + 1)
    return 0.0


def calc_hit_at_k(returned_ids: List[str], positive_ids: List[str], k: int = 5) -> int:
    """前 K 个中是否至少命中一个 (0/1)。"""
    return 1 if (set(returned_ids[:k]) & set(positive_ids)) else 0
