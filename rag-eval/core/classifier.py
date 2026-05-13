"""查询类型自动分类器 — 基于规则的六分类。"""


def classify_query(query: str) -> str:
    """根据句式、长度、关键词将中文查询分为 6 类。"""
    q = query.strip()

    # 长尾/模糊: 极短或高度口语化
    if len(q) <= 8:
        return "长尾/模糊"
    if any(w in q for w in ["这个", "那个", "怎么搞", "咋办", "搞一下", "咋回事", "啥意思", "咋整"]):
        return "长尾/模糊"

    # 是否判断: 疑问句 + 判断词
    if q.endswith(("?", "？", "吗", "么")):
        judge = ["能", "可以", "会", "是否", "是不是", "有没有", "能不能", "会不会"]
        if any(w in q for w in judge):
            return "是否判断"

    # 对比/差异
    compare = ["区别", "不同", "对比", "比较", "优缺点", "vs", "相比", "哪个好", "哪个更", "差别", "差异"]
    if any(w in q for w in compare):
        return "对比/差异"

    # 数值/列表
    list_w = ["多少", "几个", "哪些", "有哪些", "哪几个", "排名", "名单", "列表", "十大", "几种"]
    if any(w in q for w in list_w):
        return "数值/列表"

    # 描述/解释: 疑问词 + 解释词
    if q.startswith(("什么", "如何", "怎么", "为什么", "怎样")):
        return "描述/解释"
    explain = ["原理", "概念", "定义", "理解", "过程", "流程", "机制", "方法", "策略", "步骤", "方式", "因素", "原因"]
    if any(w in q for w in explain):
        return "描述/解释"

    return "精确术语"
