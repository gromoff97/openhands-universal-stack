# OpenHands Universal Stack

Reusable Docker Compose stack for `OpenHands`.

Included services:

- `OpenHands`
- `ChatMock`
- `Ollama`
- `Context7`
- `Memory MCP`

This repository contains only the infrastructure for that stack.

## Requirements

- Linux or WSL2
- Docker with `docker compose`
- Docker `buildx`
- a target project available on the Linux filesystem

For WSL, prefer a Linux path such as:

```bash
$HOME/work/my-project
```

instead of `/mnt/c/...` if you want stable git and `Changes` behavior.

## Files

- `compose.yaml`
- `.env.example`

All infrastructure is defined in `compose.yaml`. No separate Dockerfiles are required in the repository.

## Configuration

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Main variables:

- `STACK_NAME`
- `PROJECT_ROOT`
- `OPENHANDS_PORT`
- `OLLAMA_HOST_PORT`
- `CONTEXT7_API_KEY`
- `DISTILL_OLLAMA_MODEL`
- `DISTILL_TIMEOUT_MS`

Minimal example:

```env
STACK_NAME=openhands-support
PROJECT_ROOT=$HOME/work/my-project
CONTEXT7_API_KEY=
OPENHANDS_PORT=3001
OLLAMA_HOST_PORT=11435
DISTILL_OLLAMA_MODEL=qwen3.5:2b
DISTILL_TIMEOUT_MS=90000
```

## First Run

Start the stack:

```bash
docker compose up -d --build
```

Log into ChatMock once:

```bash
docker compose --profile login run --rm --service-ports chatmock-login
```

Then open `OpenHands`:

```text
http://localhost:<OPENHANDS_PORT>
```

and save your preferred settings in the GUI.

Recommended values for this stack:

- model: `gpt-5.1-codex-max`
- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`

If you want MCP enabled, add these servers once in the GUI:

- `Context7`
  - transport: `SHTTP`
  - URL: `http://context7-mcp:3000/mcp`
- `Memory`
  - transport: `SHTTP`
  - URL: `http://memory-mcp:8000/mcp`

Then restart once:

```bash
docker compose down
docker compose up -d --build
```

After that, normal usage is just:

```bash
docker compose up -d --build
docker compose down
```

## Persistence

The stack keeps its state in Docker named volumes:

- `${STACK_NAME}-openhands-state`
- `${STACK_NAME}-chatmock-state`
- `${STACK_NAME}-ollama-data`
- `${STACK_NAME}-memory-data`

That means:

- `ChatMock` login survives normal restarts
- `OpenHands` GUI settings survive normal restarts
- `Ollama` model data survives normal restarts

You only lose that state if you explicitly remove the volumes.

## Notes

- the project is mounted into the runtime at `/workspace/project`
- `distill` inside the runtime talks to `Ollama` through `host.docker.internal`
- `ChatMock` auth is stored in `${STACK_NAME}-chatmock-state`
- `OpenHands` state is stored in `${STACK_NAME}-openhands-state`
