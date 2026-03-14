FROM node:22-slim

RUN npm install -g supergateway@3.4.3 @modelcontextprotocol/server-memory@2026.1.26

ENV MEMORY_FILE_PATH=/data/memory.jsonl

VOLUME ["/data"]

EXPOSE 8000

ENTRYPOINT ["supergateway"]
CMD ["--stdio", "mcp-server-memory", "--outputTransport", "streamableHttp", "--port", "8000", "--streamableHttpPath", "/mcp", "--healthEndpoint", "/healthz"]
