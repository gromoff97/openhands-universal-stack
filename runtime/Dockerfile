FROM ghcr.io/openhands/agent-server:1.12.0-python

ARG DISTILL_MODEL=qwen3.5:2b
ARG DISTILL_TIMEOUT_MS=90000
ARG DISTILL_HOST=http://host.docker.internal:11435

USER root

RUN npm install -g @samuelfaj/distill@1.4.0

RUN mkdir -p /home/openhands/.config/distill \
    && printf '{\n  "host": "%s",\n  "model": "%s",\n  "thinking": false,\n  "timeoutMs": %s\n}\n' "${DISTILL_HOST}" "${DISTILL_MODEL}" "${DISTILL_TIMEOUT_MS}" > /home/openhands/.config/distill/config.json \
    && chown -R openhands:openhands /home/openhands/.config

USER openhands
