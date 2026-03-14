# OpenHands Universal Stack

This repository contains a reusable supporting stack around `OpenHands`.

It is intentionally separate from any project repository. The stack is parameterized through `.env`:

- `PROJECT_ROOT` selects which project is mounted into the sandbox
- `OPENHANDS_HOME` selects where OpenHands keeps project-specific state
- `STACK_NAME` names the running containers and built images

The recommended layout follows Linux/XDG conventions:

- this repo: anywhere you want, for example `~/.config/openhands-universal-stack/`
- project-specific OpenHands state: `~/.local/state/openhands/projects/<project>/`
- shared ChatMock auth/state: `~/.local/state/chatmock-openhands/`

The current defaults provide:

- `OpenHands GUI`
- `ChatMock` fixed to `gpt-5.1-codex-max` with `low` reasoning
- custom runtime image with `distill`
- `Ollama` as the `distill` backend
- `Context7`
- `Memory MCP`

## What This Repo Does

It gives you:

- a shared `OpenHands` GUI service
- a pinned `ChatMock` backend for `gpt-5.1-codex-max`
- a runtime image with `distill`
- `Ollama` for `distill`
- `Context7`
- `Memory MCP`
- automatic preseeded `OpenHands` state for each target project

It does not include any project-specific workflow files such as:

- `OPENHANDS.md`
- `PLAN.md`
- `TODO.md`
- `DECISIONS.md`
- `EVIDENCE.md`
- `logs/`

Keep those in your project repo or in your own workflow layer.

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
- `init/`: one-shot bootstrap for `settings.json` and `mcp.json`
- `.env.example`: configuration template
- `DEPLOYMENT.md`: secondary deployment reference

## Configuration

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Then edit `.env`.

Important fields:

- `STACK_NAME`
  - names containers and built images
- `PROJECT_ROOT`
  - absolute Linux path to the target project
- `OPENHANDS_HOME`
  - project-specific `OpenHands` state directory
- `CHATMOCK_HOME`
  - shared `ChatMock` auth directory
- `OPENHANDS_PORT`
  - host port for the GUI
- `OLLAMA_HOST_PORT`
  - host port for `Ollama`

Example:

```env
STACK_NAME=openhands-support
PROJECT_ROOT=$HOME/work/my-project
OPENHANDS_HOME=$HOME/.local/state/openhands/projects/my-project
CHATMOCK_HOME=$HOME/.local/state/chatmock-openhands
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
- write `settings.json` and `mcp.json` into `${OPENHANDS_HOME}`
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

The auth token is stored under `${CHATMOCK_HOME}` and reused on future runs.

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

The stack writes `OpenHands` state automatically into `${OPENHANDS_HOME}`:

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

## Files

- `compose.yaml`: shared service topology
- `chatmock/`: pinned ChatMock image
- `runtime/`: shared OpenHands runtime image with `distill`
- `context7/`: Context7 image
- `memory/`: Memory MCP over streamable HTTP
- `init/`: one-shot state bootstrap for `OpenHands`
- `.env`: active deployment target and ports

## Notes

- The mounted project appears inside the sandbox at `/workspace/project`.
- `distill` inside the sandbox talks to `Ollama` through `host.docker.internal`.
- `ChatMock` auth is stored outside projects in `${CHATMOCK_HOME}`.
- `OpenHands` state is project-specific through `${OPENHANDS_HOME}`.
- After the first successful `ChatMock` login, future runs are just `docker compose up -d --build` and `docker compose down`.
- This repository does not include project-specific workflow files such as `OPENHANDS.md`, `PLAN.md`, or `TODO.md`.
