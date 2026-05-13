#!/usr/bin/env python3
"""将 AI 改写后的查询合并到 queries.json。"""
import json, sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent

def main(rewritten_file: str):
    with open(rewritten_file, encoding='utf-8') as f:
        rewritten = json.load(f)
    rq_map = {r['query_id']: r['rewritten'] for r in rewritten}

    qf = root / 'output' / 'queries.json'
    with open(qf, encoding='utf-8') as f:
        queries = json.load(f)

    updated = 0
    for q in queries:
        if q['query_id'] in rq_map:
            q['query'] = rq_map[q['query_id']]
            updated += 1

    with open(qf, 'w', encoding='utf-8') as f:
        json.dump(queries, f, ensure_ascii=False, indent=2)

    print(f'Updated {updated}/{len(rq_map)} queries')
    for q in queries[:3]:
        print(f'  {q["query_id"]}: {q["query"][:60]}')


if __name__ == '__main__':
    main(sys.argv[1])
