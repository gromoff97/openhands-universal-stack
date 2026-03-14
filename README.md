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

## Quick Start

1. Copy `.env.example` to `.env`
2. Edit `.env`
   - set `PROJECT_ROOT` to your Linux-side project path
   - set `OPENHANDS_HOME` to a project-specific state directory
   - adjust `OPENHANDS_PORT` if needed
3. Start the stack:
   - `docker compose up -d --build`
4. Log into ChatMock once:
   - `docker compose --profile login run --rm --service-ports chatmock-login`
5. Restart once:
   - `docker compose down && docker compose up -d --build`
6. Open `http://localhost:<OPENHANDS_PORT>`

For a full step-by-step deployment walkthrough, read `DEPLOYMENT.md`.

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
