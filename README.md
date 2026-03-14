# OpenHands Universal Stack

Reusable Docker Compose stack for `OpenHands` with:

- `ChatMock` fixed to `gpt-5.1-codex-max` with `low` reasoning
- `Ollama` for `distill`
- `Context7`
- `Memory MCP`
- automatic preseeded `OpenHands` settings and MCP config

This repository is infrastructure only. It does not include project-local workflow files such as:

- `OPENHANDS.md`
- `PLAN.md`
- `TODO.md`
- `DECISIONS.md`
- `EVIDENCE.md`
- `logs/`

Keep those in the target project or in your own workflow layer.

## Prerequisites

You need:

- Linux or WSL2
- Docker with `docker compose`
- Docker `buildx`
- a project located on the Linux filesystem, not on `/mnt/c/...`, if you want stable git and `Changes` behavior

Recommended project location:

```bash
mkdir -p "$HOME/work"
rsync -a /path/to/source-project/ "$HOME/work/my-project/"
```

## Repository Layout

- `compose.yaml`: service topology
- `chatmock/`: pinned ChatMock image
- `runtime/`: runtime image with `distill`
- `context7/`: Context7 image
- `memory/`: Memory MCP image
- `.env.example`: configuration template

## Configuration

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Then edit `.env`.

Important fields:

- `STACK_NAME`
  - names containers, built images, and persistent Docker volumes
- `PROJECT_ROOT`
  - absolute Linux path to the target project
- `OPENHANDS_PORT`
  - host port for the GUI
- `OLLAMA_HOST_PORT`
  - host port for `Ollama`
- `CONTEXT7_API_KEY`
  - optional

Example:

```env
STACK_NAME=openhands-support
PROJECT_ROOT=$HOME/work/my-project
CONTEXT7_API_KEY=
OPENHANDS_PORT=3001
OLLAMA_HOST_PORT=11435
DISTILL_OLLAMA_MODEL=qwen3.5:2b
DISTILL_TIMEOUT_MS=90000
```

## First-Time Setup

### 1. Build and start the stack

```bash
docker compose up -d --build
```

This will:

- build the local images
- start `ChatMock`, `Ollama`, `Context7`, and `Memory MCP`
- create the shared runtime image used by `OpenHands`
- write `OpenHands` settings into the persistent Docker state volume
- preconfigure `OpenHands` to use:
  - `gpt-5.1-codex-max`
  - `http://chatmock:5000/v1`
  - `Context7`
  - `Memory MCP`

### 2. Log into ChatMock once

```bash
docker compose --profile login run --rm --service-ports chatmock-login
```

Then:

1. open the printed auth URL
2. sign in to the desired ChatGPT account
3. finish the callback flow

The auth token is stored in the persistent Docker volume `${STACK_NAME}-chatmock-state` and reused on future runs.

### 3. Restart once after login

```bash
docker compose down
docker compose up -d --build
```

After that, the normal cycle is just:

```bash
docker compose up -d --build
docker compose down
```

No extra GUI configuration should be needed unless the ChatMock auth expires.

## Open the GUI

Open:

```text
http://localhost:<OPENHANDS_PORT>
```

For example, if `OPENHANDS_PORT=3001`, open `http://localhost:3001`.

## What Gets Preseeded

The stack writes `OpenHands` state automatically into the persistent Docker volume `${STACK_NAME}-openhands-state`:

- model: `gpt-5.1-codex-max`
- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`
- agent: `CodeActAgent`
- condenser size: `240`
- memory condensation: `ON`
- confirmation mode: `OFF`
- `Context7` and `Memory MCP` pre-registered in MCP config

## Daily Usage

Start:

```bash
docker compose up -d --build
```

Stop:

```bash
docker compose down
```

Re-login if ChatMock auth expires:

```bash
docker compose --profile login run --rm --service-ports chatmock-login
docker compose down
docker compose up -d --build
```

## Troubleshooting

If `Changes` fails in `OpenHands`:

- move the project to a Linux path such as `$HOME/work/my-project`
- avoid `/mnt/c/...`

If the GUI starts but you cannot use the model:

- rerun the `chatmock-login` command
- restart the stack

If a port is busy:

- change `OPENHANDS_PORT` or `OLLAMA_HOST_PORT` in `.env`
- restart the stack

## Notes

- The mounted project appears inside the sandbox at `/workspace/project`.
- `distill` inside the sandbox talks to `Ollama` through `host.docker.internal`.
- `ChatMock` auth is stored in the Docker volume `${STACK_NAME}-chatmock-state`.
- `OpenHands` state is stored in the Docker volume `${STACK_NAME}-openhands-state`.
- After the first successful `ChatMock` login, future runs are just `docker compose up -d --build` and `docker compose down`.
