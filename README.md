# OpenHands Universal Stack

A minimal setup for `OpenHands`.

## Install

1. Optional: copy `.env.example` to `.env` if you want to override defaults:

```bash
cp .env.example .env
```

2. Start the stack:

```bash
docker compose up -d --build
```

3. Log into `ChatMock` once:

```bash
docker compose --profile login run --rm --service-ports chatmock-login
```

4. Open `OpenHands`:

```text
http://localhost:3001
```

5. Save these LLM settings once:

- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`
- model: `gpt-5.1-codex-max`

6. Add these MCP servers once:

- `Context7`
  - transport: `SHTTP`
  - URL: `http://context7-mcp:3000/mcp`
- `Memory`
  - transport: `SHTTP`
  - URL: `http://memory-mcp:8000/mcp`

7. Start working:

- open `OpenHands`
- use `Connect Repo`
- select the GitHub repository you want to work on

## Daily Use

```bash
docker compose up -d --build
docker compose down
```

For overrides, persistence, and other details, see [REFERENCE.md](REFERENCE.md).
