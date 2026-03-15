# OpenHands Universal Stack

## Install

### 1. Terminal Setup

```bash
# Optional: create .env only if you want to override the built-in defaults
cp .env.example .env

# Build and start the full stack
docker compose up -d --build

# One-time ChatMock login
docker compose --profile login run --rm --service-ports chatmock-login
```

### 2. OpenHands Setup (http://localhost:3001)

- LLM:
  - base URL: `http://chatmock:5000/v1`
  - API key: any non-empty value, for example `chatmock`
  - model: `CHATMOCK_MODEL` (default `gpt-5.1-codex-max`)
- MCP:
  - `Context7` -> `SHTTP` -> `http://context7-mcp:3000/mcp`
  - `Memory` -> `SHTTP` -> `http://memory-mcp:8000/mcp`
- `Connect Repo` -> choose the GitHub repository you want to work on
- Daily use:

```bash
# Start the stack again later
docker compose up -d --build

# Stop the stack
docker compose down
```
