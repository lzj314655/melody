# ============ 麦乐迪 AI 智能助手：FastAPI + DeepSeek + RAG ============
FROM python:3.11-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/ \
    HF_ENDPOINT=https://hf-mirror.com \
    TZ=Asia/Shanghai

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 应用代码 + 知识库 + 建库脚本
COPY run.py .
COPY app ./app
COPY knowledge ./knowledge
COPY scripts ./scripts
COPY sql ./sql

# 构建期预热：下载 int8 向量模型（~24MB，运行期离线可用）
RUN python -c "from app.knowledge import get_kb; get_kb().model.ensure_files()"

# .env 不打入镜像（compose 注入环境变量）；DEEPSEEK_API_KEY 必须由环境提供
EXPOSE 8100
CMD ["sh", "-c", "python scripts/build_kb.py >/dev/null 2>&1 || true; python run.py"]
