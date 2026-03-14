FROM python:3.13-slim

ARG CHATMOCK_COMMIT=baf312a02e54dd62ab74be485759da5ee9ccf226

WORKDIR /src

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/RayBytes/ChatMock.git . \
    && git checkout "${CHATMOCK_COMMIT}" \
    && pip install --no-cache-dir .

ENV CHATGPT_LOCAL_HOME=/config
ENV PYTHONUNBUFFERED=1

VOLUME ["/config"]

ENTRYPOINT ["chatmock"]
CMD ["serve", "--host", "0.0.0.0", "--port", "5000", "--debug-model", "gpt-5.1-codex-max", "--reasoning-effort", "low", "--reasoning-summary", "none"]
