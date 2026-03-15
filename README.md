# OpenHands Universal Stack

Reusable Docker Compose stack for `OpenHands` with:

- `ChatMock`
- `Ollama`
- `Context7 MCP`
- `Memory MCP`
- a custom OpenHands sandbox image that includes `distill`

This repository contains infrastructure only.

## Compose Layout

The stack is organized by responsibility:

- long-lived services
  - `openhands`
  - `chatmock`
  - `ollama`
  - `context7-mcp`
  - `memory-mcp`
- one-shot jobs
  - `chatmock-login`
  - `ollama-pull`
- build job
  - `runtime-build`

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

Required variables:

- `STACK_NAME`
- `OH_SECRET_KEY`
- `OPENHANDS_PORT`
- `OLLAMA_HOST_PORT`
- `CHATMOCK_MODEL`
- `CHATMOCK_REASONING_EFFORT`
- `CHATMOCK_REASONING_SUMMARY`
- `DISTILL_OLLAMA_MODEL`
- `DISTILL_TIMEOUT_MS`

Optional:

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

## First Run

Start the stack:

```bash
docker compose up -d --build
```

Log into `ChatMock` once:

```bash
docker compose --profile login run --rm --service-ports chatmock-login
```

Open `OpenHands`:

```text
http://localhost:<OPENHANDS_PORT>
```

Save the LLM settings once:

- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`
- model: use `CHATMOCK_MODEL`

This stack applies a small build-time patch to `ChatMock` so it can answer
`/v1/responses`, which current `OpenHands` expects from an OpenAI-compatible
backend.

Then add these MCP servers once:

- `Context7`
  - transport: `SHTTP`
  - URL: `http://context7-mcp:3000/mcp`
- `Memory`
  - transport: `SHTTP`
  - URL: `http://memory-mcp:8000/mcp`

At this point the sandbox starts with an empty workspace. To work on a real
repository:

- open `OpenHands`
- use `Connect Repo`
- select the GitHub repository you want to work on

By default, the stack does not mount any local project into the sandbox and
uses the standard ephemeral sandbox workspace.

After that, normal usage is:

```bash
docker compose up -d --build
docker compose down
```

## Persistence

The stack keeps state in:

- Docker named volumes
  - `${STACK_NAME}-chatmock-state`
  - `${STACK_NAME}-ollama-data`
  - `${STACK_NAME}-memory-data`
- standard OpenHands host state
  - `${HOME}/.openhands`

That means:

- `ChatMock` login survives normal restarts
- `Ollama` model data survives normal restarts
- `Memory MCP` data survives normal restarts
- `OpenHands` settings, MCP config, and conversation state survive normal restarts

## Reset State

`docker compose down` stops containers and keeps all state.

`docker compose down -v` removes Docker named volumes for:

- `ChatMock`
- `Ollama`
- `Memory MCP`

It does not remove `OpenHands` host state in `${HOME}/.openhands`.

If you also want a fresh `OpenHands` state, remove that directory yourself:

```bash
rm -rf "${HOME}/.openhands"
```

## Runtime Notes

- the sandbox uses the standard internal ephemeral workspace until you connect a repo
- `OpenHands` uses `${STACK_NAME}-runtime:latest` as its sandbox image
- `distill` inside the sandbox talks to `Ollama` through `host.docker.internal`
