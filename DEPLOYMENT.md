# Deployment Guide

This guide explains how to deploy the full shared supporting stack for any project from scratch.

## 1. What Lives Where

There are three layers:

1. `OpenHands`
   - the GUI and agent runtime
2. `Supporting stack`
   - this directory
   - `docker compose`
   - `ChatMock`
   - `Ollama`
   - `Context7`
   - `Memory MCP`
3. `Project context layer`
   - files inside the project such as `OPENHANDS.md`, `PLAN.md`, `TODO.md`, `DECISIONS.md`, `EVIDENCE.md`, `logs/`

The supporting stack is shared. The project context files remain project-local.

## 2. Put the Project in WSL

Do not use a project directly from `/mnt/c/...` if you want stable git and `Changes` behavior.

Recommended:

```bash
mkdir -p "$HOME/work"
rsync -a /path/to/source-project/ "$HOME/work/my-project/"
```

Your project should end up at a Linux path like:

```bash
$HOME/work/my-project
```

## 3. Keep Project Workflow Files Separate

If you use project-local workflow files such as:

- `OPENHANDS.md`
- `PLAN.md`
- `TODO.md`
- `DECISIONS.md`
- `EVIDENCE.md`
- `logs/`

keep them in the target project repository or in your own workflow layer.

They are not part of this infrastructure repository.

## 4. Configure the Shared Supporting Stack

Open:

```bash
cd /path/to/openhands-universal-stack
```

Copy `.env.example` to `.env`, then edit `.env`.

Fields you must care about:

- `STACK_NAME`
  - name for containers and built images
  - usually leave as `openhands-support`
- `PROJECT_ROOT`
  - absolute Linux path to the project
- `OPENHANDS_HOME`
  - project-specific OpenHands state directory
- `CHATMOCK_HOME`
  - shared ChatMock auth directory
- `OPENHANDS_PORT`
  - GUI port
- `OLLAMA_HOST_PORT`
  - host port exposed for Ollama

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

## 5. First Start the Full Stack

Run:

```bash
cd /path/to/openhands-universal-stack
docker compose up -d --build
```

What this does:

- builds the supporting images
- creates the shared runtime image used by `OpenHands`
- writes `settings.json` and `mcp.json` into `${OPENHANDS_HOME}`
- starts `OpenHands`, `ChatMock`, `Ollama`, `Context7`, and `Memory MCP`
- pulls the configured Ollama model

## 6. Log Into ChatMock Once

Run:

```bash
cd /path/to/openhands-universal-stack
docker compose --profile login run --rm --service-ports chatmock-login
```

Then:

1. open the printed auth URL
2. log into the desired ChatGPT account
3. finish the callback flow

The auth token is stored under `${CHATMOCK_HOME}` and reused later.

## 7. Restart Once After Login

Run:

```bash
cd /path/to/openhands-universal-stack
docker compose down
docker compose up -d --build
```

From this point on, your normal daily workflow is only:

```bash
docker compose up -d --build
docker compose down
```

You should not need to log in or reconfigure the GUI again unless the `ChatMock` auth expires.

## 8. Open the GUI

Open:

```text
http://localhost:3001
```

Or whatever value you set in `OPENHANDS_PORT`.

## 9. GUI Settings Are Preseeded

The stack now writes the required `OpenHands` state automatically into `${OPENHANDS_HOME}`:

- model: `gpt-5.1-codex-max`
- base URL: `http://chatmock:5000/v1`
- API key: `chatmock`
- agent: `CodeActAgent`
- condenser size: `240`
- memory condensation: `ON`
- confirmation mode: `OFF`
- `Context7` and `Memory MCP` are pre-registered

You no longer need to click through the settings UI for every fresh deployment target.

## 10. Smoke Test First

For a clean smoke test, do not add MCP servers immediately.

Create a new conversation and send:

```text
Read OPENHANDS.md, PLAN.md, TODO.md, DECISIONS.md, and EVIDENCE.md first.

Then verify without making code changes:
1. the project is mounted at /workspace/project
2. distill is available
3. you can inspect the repository structure

Do not make code changes.
Return only a brief summary.
```

## 11. Day-to-Day Commands

Start:

```bash
cd /path/to/openhands-universal-stack
docker compose up -d --build
```

Stop:

```bash
cd /path/to/openhands-universal-stack
docker compose down
```

Re-login ChatMock:

```bash
cd /path/to/openhands-universal-stack
docker compose --profile login run --rm --service-ports chatmock-login
```

## 12. What Not to Put in the Shared Stack

Do not move project workflow files here.

Examples:

- `OPENHANDS.md`
- `PLAN.md`
- `TODO.md`
- `DECISIONS.md`
- `EVIDENCE.md`
- `logs/`

## 13. Troubleshooting

If the GUI works but `Changes` fails:

- confirm the project is on Linux FS, not `/mnt/c/...`
- restart the stack
- create a new conversation instead of reusing a broken one

If the GUI asks for login again:

- rerun `docker compose --profile login run --rm --service-ports chatmock-login`
- then restart the stack

If the port is busy:

- change `OPENHANDS_PORT` or `OLLAMA_HOST_PORT` in `.env`
- restart the stack
