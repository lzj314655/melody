"""RAG 知识检索：int8 量化中文向量模型 + 轻量 numpy 向量库（data/vecstore.npz）。

设计取舍（针对低内存演示机）：
- 向量模型 BAAI/bge-small-zh-v1.5 的 int8 量化 ONNX（Xenova 移植，约 24MB），
  直接用 onnxruntime + tokenizers 推理，不引入 fastembed/transformers 重依赖链，
  ONNX 会话峰值内存约为 fp32 版的 1/4。
- 知识库仅几十个块，numpy 精确余弦检索即可（微秒级），无需 Chroma/FAISS。
- 模型文件自动从 hf-mirror 下载到 data/embed-model/，之后完全离线。

bge 系列检索时 query 建议加指令前缀，文档侧不加。
"""
import json
import logging
from pathlib import Path

import numpy as np

from .config import settings

log = logging.getLogger(__name__)

_REPO = "Xenova/bge-small-zh-v1.5"
_MODEL_FILES = {  # 相对仓库路径 -> 本地文件名
    "onnx/model_quantized.onnx": "model_int8.onnx",
    "tokenizer.json": "tokenizer.json",
}
_QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章："
_MAX_LEN = 512
_BATCH = 1  # 低内存机器：batch=1 时注意力中间矩阵仅 ~8MB

_kb = None  # KnowledgeBase 单例


def _download(url: str, dest: Path):
    import httpx
    log.info("[kb] downloading %s ...", url)
    with httpx.Client(timeout=120, follow_redirects=True) as c:
        with c.stream("GET", url) as r:
            r.raise_for_status()
            tmp = dest.with_suffix(dest.suffix + ".part")
            with open(tmp, "wb") as f:
                for chunk in r.iter_bytes(1 << 16):
                    f.write(chunk)
            tmp.rename(dest)


class BgeInt8Embedder:
    """tokenizer + int8 ONNX BERT，CLS 池化 + L2 归一化。"""

    def __init__(self, model_dir: Path):
        self.dir = model_dir
        self._tok = None
        self._sess = None

    def ensure_files(self):
        import os
        self.dir.mkdir(parents=True, exist_ok=True)
        endpoint = os.environ.get("HF_ENDPOINT", "https://hf-mirror.com")
        for rel, name in _MODEL_FILES.items():
            dest = self.dir / name
            if not dest.exists():
                _download(f"{endpoint}/{_REPO}/resolve/main/{rel}", dest)

    def _ensure_loaded(self):
        if self._sess is not None:
            return
        import os
        import time
        self.ensure_files()
        os.environ.setdefault("OMP_NUM_THREADS", "2")
        from tokenizers import Tokenizer
        import onnxruntime as ort
        so = ort.SessionOptions()
        so.intra_op_num_threads = 2
        so.enable_cpu_mem_arena = False   # 低内存：不做 arena 预留，按需分配
        so.enable_mem_pattern = False
        so.log_severity_level = 3
        self._tok = Tokenizer.from_file(str(self.dir / "tokenizer.json"))
        self._tok.enable_truncation(max_length=_MAX_LEN)
        self._tok.enable_padding()  # pad 到批内最长
        # 演示机提交内存紧张时初始化可能偶发 bad allocation，退避重试
        last_err: Exception | None = None
        for attempt in range(6):
            try:
                self._sess = ort.InferenceSession(str(self.dir / "model_int8.onnx"),
                                                  sess_options=so,
                                                  providers=["CPUExecutionProvider"])
                break
            except Exception as e:  # noqa: BLE001
                last_err = e
                log.warning("[kb] onnx session init 失败(第%s次)：%s", attempt + 1, str(e)[:100])
                time.sleep(3)
        if self._sess is None:
            raise last_err  # type: ignore[misc]

    def embed(self, texts: list[str]) -> np.ndarray:
        self._ensure_loaded()
        vecs = []
        for i in range(0, len(texts), _BATCH):
            batch = texts[i:i + _BATCH]
            encs = self._tok.encode_batch(batch)
            feed = {
                "input_ids": np.asarray([e.ids for e in encs], dtype=np.int64),
                "attention_mask": np.asarray([e.attention_mask for e in encs], dtype=np.int64),
                "token_type_ids": np.asarray([e.type_ids for e in encs], dtype=np.int64),
            }
            # 兼容不含 token_type_ids 的导出版本
            names = {i.name for i in self._sess.get_inputs()}
            if "token_type_ids" not in names:
                feed.pop("token_type_ids")
            hidden = self._sess.run(None, feed)[0]  # (batch, seq, dim)
            cls = hidden[:, 0, :].astype(np.float32)  # CLS 池化
            vecs.append(cls)
        mat = np.concatenate(vecs, axis=0)
        norms = np.linalg.norm(mat, axis=1, keepdims=True)
        norms[norms == 0] = 1.0
        return mat / norms

    def embed_query(self, text: str) -> np.ndarray:
        return self.embed([_QUERY_INSTRUCTION + text])[0]


class KnowledgeBase:
    """向量库：ids / 向量矩阵 / 原文 / 元数据，持久化到 data/vecstore.npz。"""

    INDEX_FILE = "vecstore.npz"
    MODEL_DIR = "embed-model"

    def __init__(self, data_dir: str | None = None):
        self.data_dir = Path(data_dir) if data_dir else Path(settings.chroma_dir).parent  # data/
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.model = BgeInt8Embedder(self.data_dir / self.MODEL_DIR)
        self.ids: list[str] = []
        self.texts: list[str] = []
        self.sources: list[str] = []
        self.headings: list[str] = []
        self.vectors: np.ndarray | None = None  # (n, dim) float32，已归一化
        self._load()

    @property
    def index_path(self) -> Path:
        return self.data_dir / self.INDEX_FILE

    def _load(self):
        if not self.index_path.exists():
            return
        z = np.load(self.index_path, allow_pickle=False)
        self.ids = [str(x) for x in z["ids"].tolist()]
        self.texts = z["texts"].tolist()
        self.sources = z["sources"].tolist()
        self.headings = z["headings"].tolist()
        self.vectors = z["vectors"].astype(np.float32)

    def _save(self):
        np.savez(self.index_path,
                 ids=np.asarray(self.ids, dtype=np.str_),
                 texts=np.asarray(self.texts, dtype=np.str_),
                 sources=np.asarray(self.sources, dtype=np.str_),
                 headings=np.asarray(self.headings, dtype=np.str_),
                 vectors=self.vectors)

    # ---- 构建期（build_kb.py 使用）----

    def rebuild(self, chunks: list[dict]):
        if not chunks:
            raise ValueError("知识库切块为空")
        vectors = self.model.embed([c["text"] for c in chunks])
        self.ids = [c["id"] for c in chunks]
        self.texts = [c["text"] for c in chunks]
        self.sources = [c["source"] for c in chunks]
        self.headings = [c["heading"] for c in chunks]
        self.vectors = vectors
        self._save()
        log.info("[kb] rebuilt: %d chunks -> %s", len(chunks), self.index_path)

    def count(self) -> int:
        return len(self.ids)

    # ---- 检索期 ----

    def search(self, query: str, top_k: int | None = None) -> list[dict]:
        k = top_k or settings.kb_top_k
        if self.count() == 0:
            return []
        qv = self.model.embed_query(query)
        sims = self.vectors @ qv  # 已归一化，点积即余弦相似度
        order = np.argsort(-sims)[: min(k, len(sims))]
        return [{"text": self.texts[i], "source": self.sources[i],
                 "heading": self.headings[i], "score": round(float(sims[i]), 4)}
                for i in order]


def get_kb() -> KnowledgeBase:
    global _kb
    if _kb is None:
        _kb = KnowledgeBase()
    return _kb


def chunk_markdown(text: str, source: str) -> list[dict]:
    """按 ## 二级标题切块；块超长再按空行二次切分（目标 300~500 字）。"""
    lines = text.splitlines()
    chunks: list[dict] = []
    current_heading = ""
    buf: list[str] = []

    def flush():
        body = "\n".join(buf).strip()
        buf.clear()
        if not body:
            return
        for piece in _split_long(body):
            chunks.append({
                "id": f"{source}#{current_heading}#{len(chunks)}",
                "text": f"【{source} - {current_heading}】\n{piece}" if current_heading else piece,
                "source": source,
                "heading": current_heading,
            })

    def _split_long(body: str) -> list[str]:
        if len(body) <= 600:
            return [body]
        paras, cur = [], ""
        for para in body.split("\n\n"):
            if len(cur) + len(para) > 500 and cur:
                paras.append(cur)
                cur = para
            else:
                cur = f"{cur}\n\n{para}".strip("\n")
        if cur:
            paras.append(cur)
        return paras

    for line in lines:
        if line.startswith("## "):
            flush()
            current_heading = line[3:].strip()
            buf.append(line)
        elif line.startswith("# ") and not current_heading:
            continue  # 文件主标题不入块
        else:
            buf.append(line)
    flush()
    return chunks


def load_knowledge_files(kb_dir: str | None = None) -> list[dict]:
    kb_dir = Path(kb_dir or settings.kb_dir)
    all_chunks = []
    for md in sorted(kb_dir.glob("*.md")):
        text = md.read_text(encoding="utf-8")
        all_chunks.extend(chunk_markdown(text, md.stem))
    return all_chunks


if __name__ == "__main__":  # python -m app.knowledge 自测分块
    cs = load_knowledge_files()
    print(json.dumps({"chunks": len(cs), "sample": cs[0]["id"] if cs else None},
                     ensure_ascii=False, indent=2))
