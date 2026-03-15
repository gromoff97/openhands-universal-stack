# OpenHands Universal Stack

## Install

### 1. Do in your environment

```bash
# Create the required environment file with working defaults
cp .env.example .env

# Build and start the full stack
docker compose up -d --build

# One-time login
docker compose run --rm -P chatmock-login
```

### 2. Do in `OpenHands` `http://localhost:3001`

- LLM -> base URL: `http://chatmock:5000/v1`
- LLM -> API key: any non-empty value
- LLM -> model: any OpenAI-style model id (`gpt-5.1-codex-max` is the default upstream target)
- MCP -> `Context7` -> `SHTTP` -> `http://context7-mcp:3000/mcp`
- MCP -> `Memory` -> `SHTTP` -> `http://memory-mcp:8000/mcp`
- `Connect Repo` -> choose the GitHub repository you want to work on

Done! Daily use:

```bash
docker compose up -d --build
docker compose down
```
