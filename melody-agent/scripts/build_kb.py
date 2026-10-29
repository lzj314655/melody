"""构建 RAG 向量知识库：读取 knowledge/*.md → 分块 → 向量化 → 写入 Chroma。

用法（在 melody-agent 目录，venv 已激活）：
    python scripts/build_kb.py            # 重建并跑内置检索自测
    python scripts/build_kb.py "怎么退钱"  # 重建后用指定问题试检索
可重复执行（先清空旧库）。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.knowledge import get_kb, load_knowledge_files  # noqa: E402


def main():
    kb = get_kb()
    chunks = load_knowledge_files()
    kb.rebuild(chunks)
    print(f"[build_kb] 知识库构建完成：{len(chunks)} 个块，来源：")
    sources: dict[str, int] = {}
    for c in chunks:
        sources[c["source"]] = sources.get(c["source"], 0) + 1
    for s, n in sources.items():
        print(f"  - knowledge/{s}.md  ({n} 块)")

    query = sys.argv[1] if len(sys.argv) > 1 else "优惠券怎么使用"
    print(f"\n[build_kb] 检索自测 query={query!r}")
    for hit in kb.search(query):
        preview = hit["text"].replace("\n", " ")[:80]
        print(f"  score={hit['score']:.3f}  {hit['source']} › {hit['heading']}  | {preview}…")


if __name__ == "__main__":
    main()
