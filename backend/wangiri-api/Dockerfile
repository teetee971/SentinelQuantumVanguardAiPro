FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --requirement requirements.txt \
    && addgroup --system sentinel \
    && adduser --system --ingroup sentinel --no-create-home sentinel

COPY --chown=sentinel:sentinel app_redis.py ./

USER sentinel

EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD python -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.getenv('PORT','10000')+'/health/live', timeout=2)" || exit 1

CMD ["sh", "-c", "exec uvicorn app_redis:app --host 0.0.0.0 --port ${PORT:-10000} --workers 1"]
