# OpenHands Universal Stack

A minimal setup for `OpenHands`.

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

### 2. OpenHands Setup

- Open `http://localhost:3001`
- Save LLM settings:
  - base URL: `http://chatmock:5000/v1`
  - API key: `chatmock`
  - model: `gpt-5.1-codex-max`
- Add MCP servers:
  - `Context7` -> `SHTTP` -> `http://context7-mcp:3000/mcp`
  - `Memory` -> `SHTTP` -> `http://memory-mcp:8000/mcp`
- Click `Connect Repo` and choose the GitHub repository you want to work on

## Daily Use

```bash
# Start the stack again later
docker compose up -d --build

# Stop the stack
docker compose down
```

For overrides, persistence, and other details, see [REFERENCE.md](REFERENCE.md).
