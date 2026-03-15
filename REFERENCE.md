# OpenHands Universal Stack Reference

## Overview

This repository provides infrastructure for a self-hosted `OpenHands` stack
with:

- `OpenHands`
- `ChatMock`
- `adapter`
- `Ollama`
- `Context7 MCP`
- `Memory MCP`
- a custom OpenHands sandbox image that includes `distill`

The stack is designed to behave like default local `OpenHands` as much as
possible:

- `OpenHands` state lives in `${HOME}/.openhands`
- the sandbox starts with the standard internal ephemeral workspace
- no local project is mounted by default
- repositories are expected to be attached through `Connect Repo`

## Compose Layout

The stack is organized by responsibility:

- long-lived services
  - `openhands`
  - `chatmock`
  - `adapter`
  - `ollama`
  - `context7-mcp`
  - `memory-mcp`
- one-shot jobs
  - `chatmock-login`
  - `ollama-pull`
- build job
  - `runtime-build`

## Environment Variables

User-facing configuration lives in `.env`:

- `STACK_NAME`
  - Compose project name and image prefix
- `OH_SECRET_KEY`
  - OpenHands application secret
- `OPENHANDS_PORT`
  - host port for the OpenHands UI
- `OLLAMA_HOST_PORT`
  - host port for Ollama
- `CHATMOCK_MODEL`
  - upstream ChatGPT model selected by `ChatMock`
- `CHATMOCK_REASONING_EFFORT`
  - upstream reasoning effort for `ChatMock`
- `CHATMOCK_REASONING_SUMMARY`
  - upstream reasoning summary mode for `ChatMock`
- `DISTILL_OLLAMA_MODEL`
  - model used by `distill`
- `DISTILL_TIMEOUT_MS`
  - timeout for `distill`
- `CONTEXT7_API_KEY`
  - optional key for Context7

Implementation pins stay in [compose.yaml](compose.yaml), not in `.env`.

## Persistence

The stack keeps state in:

- Docker named volumes
  - `${STACK_NAME}-chatmock-state`
  - `${STACK_NAME}-ollama-data`
  - `${STACK_NAME}-memory-data`
- standard OpenHands host state
  - `${HOME}/.openhands`

This means:

- `ChatMock` login survives normal restarts
- `Ollama` model data survives normal restarts
- `Memory MCP` data survives normal restarts
- `OpenHands` settings, MCP config, and conversation state survive normal restarts

## Reset State

`docker compose down` stops containers and keeps all state.

`docker compose down -v` removes Docker named volumes for:

- `chatmock`
- `Ollama`
- `Memory MCP`

It does not remove `OpenHands` host state in `${HOME}/.openhands`.

If you also want a fresh `OpenHands` state, remove that directory yourself:

```bash
rm -rf "${HOME}/.openhands"
```

## Runtime Notes

- `OpenHands` uses `${STACK_NAME}-runtime:latest` as its sandbox image
- `distill` inside the sandbox talks to `Ollama` through `host.docker.internal`
- `Context7` and `Memory` are exposed to OpenHands as MCP servers
- the sandbox workspace is ephemeral until you connect a repository

## Chat Backend

Current `OpenHands` uses `/v1/responses` with OpenAI-compatible backends for
`gpt-5*` models.

Stock Python `ChatMock` provides the core OpenAI-compatible backend and login
flow, but it does not expose `/v1/responses`.

This stack therefore splits the chat layer into two long-lived services:

- `chatmock`
  - the stock Python `ChatMock` backend
  - keeps login state in the shared `chatmock-state` volume
  - serves the stock endpoints such as `/v1/models`, `/v1/chat/completions`,
    and `/v1/completions`
- `adapter`
  - the front door for `OpenHands`
  - proxies the stock routes to `chatmock`
  - adds a compatibility `/v1/responses` endpoint for `OpenHands`
  - is implemented as a small stateless Kotlin `http4k` service
  - keeps no state, mounts no volumes, reads no `.env`, and has no own config
  - is fixed to the backend role `http://chatmock:8000`
  - is responsible only for protocol translation between `OpenHands` and `ChatMock`

It also supports forcing the real upstream ChatGPT model through
`CHATMOCK_MODEL` while keeping the `OpenHands`-side base URL unchanged. The
adapter itself does not read that setting; it just translates and forwards
requests to `chatmock`.

That means:

- `OpenHands` should use `http://host.docker.internal:5000/v1`
- the model name entered in the UI can be the real OpenAI-style model id, for
  example `openai/gpt-5.1-codex-max`
- the actual upstream ChatGPT model is controlled by `CHATMOCK_MODEL`

`host.docker.internal` is required because the agent sandbox runs in its own
container and cannot resolve compose service names like `chatmock`.

## UI Settings Reference

Expected LLM settings in the OpenHands UI:

- base URL: `http://host.docker.internal:5000/v1`
- API key: any non-empty value
- model: `openai/gpt-5.1-codex-max`; `CHATMOCK_MODEL` controls the real upstream target

Expected MCP settings:

- `Context7`
  - transport: `SHTTP`
  - URL: `http://context7-mcp:3000/mcp`
- `Memory`
  - transport: `SHTTP`
  - URL: `http://memory-mcp:8000/mcp`
