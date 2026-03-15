# OpenHands Universal Stack

Quick setup for a reusable Docker Compose stack with:

- `ChatMock`
- `Ollama`
- `Context7 MCP`
- `Memory MCP`
- a custom OpenHands sandbox image that includes `distill`

This repository contains infrastructure only. For architecture, persistence,
reset procedures, and runtime notes, see [REFERENCE.md](REFERENCE.md).

## Requirements

- Linux or WSL2
- Docker with `docker compose`
- Docker `buildx`
- a GitHub repository you plan to connect from the UI

## Configure

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Set these values in `.env`:

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

Minimal example:

```env
STACK_NAME=openhands-support
OH_SECRET_KEY=openhands-universal-stack-dev-secret
OPENHANDS_PORT=3001
OLLAMA_HOST_PORT=11435
CHATMOCK_MODEL=gpt-5.1-codex-max
CHATMOCK_REASONING_EFFORT=low
CHATMOCK_REASONING_SUMMARY=none
DISTILL_OLLAMA_MODEL=qwen3.5:2b
DISTILL_TIMEOUT_MS=90000
CONTEXT7_API_KEY=
```

## Quick Setup

1. Start the stack:

```bash
docker compose up -d --build
```

2. Log into `ChatMock` once:

```bash
docker compose --profile login run --rm --service-ports chatmock-login
```

3. Open `OpenHands`:

```text
http://localhost:<OPENHANDS_PORT>
```

4. Save the LLM settings once:

- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`
- model: use `CHATMOCK_MODEL`

5. Add these MCP servers once:

- `Context7`
  - transport: `SHTTP`
  - URL: `http://context7-mcp:3000/mcp`
- `Memory`
  - transport: `SHTTP`
  - URL: `http://memory-mcp:8000/mcp`

6. Start working:

- open `OpenHands`
- use `Connect Repo`
- select the GitHub repository you want to work on

The sandbox starts with the standard ephemeral workspace. No local project is
mounted by default.

## Daily Use

```bash
docker compose up -d --build
docker compose down
```
