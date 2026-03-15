# OpenHands Universal Stack

A run-and-go Docker Compose stack for `OpenHands`.

For architecture, persistence, and other details, see [REFERENCE.md](REFERENCE.md).

## Requirements

- Linux or WSL2
- Docker with `docker compose`
- Docker `buildx`
- a GitHub repository you plan to connect from the UI

## Install

1. Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

2. Set these values in `.env`:

- `STACK_NAME`
- `OH_SECRET_KEY`
- `OPENHANDS_PORT`
- `OLLAMA_HOST_PORT`
- `CHATMOCK_MODEL`
- `CHATMOCK_REASONING_EFFORT`
- `CHATMOCK_REASONING_SUMMARY`
- `DISTILL_OLLAMA_MODEL`
- `DISTILL_TIMEOUT_MS`
- `CONTEXT7_API_KEY`

3. Start the stack:

```bash
docker compose up -d --build
```

4. Log into `ChatMock` once:

```bash
docker compose --profile login run --rm --service-ports chatmock-login
```

5. Open `OpenHands`:

```text
http://localhost:<OPENHANDS_PORT>
```

6. Save these LLM settings once:

- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`
- model: use `CHATMOCK_MODEL`

7. Add these MCP servers once:

- `Context7`
  - transport: `SHTTP`
  - URL: `http://context7-mcp:3000/mcp`
- `Memory`
  - transport: `SHTTP`
  - URL: `http://memory-mcp:8000/mcp`

8. Start working:

- open `OpenHands`
- use `Connect Repo`
- select the GitHub repository you want to work on

## Daily Use

```bash
docker compose up -d --build
docker compose down
```
