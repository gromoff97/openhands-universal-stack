import json
import os
from pathlib import Path


STATE_DIR = Path("/state")
SETTINGS_PATH = STATE_DIR / "settings.json"
MCP_PATH = STATE_DIR / "mcp.json"
RUNTIME_IMAGE = f"{os.environ.get('STACK_NAME', 'openhands-support')}-runtime:latest"
CONTEXT7_API_KEY = os.environ.get("CONTEXT7_API_KEY", "").strip()


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


STATE_DIR.mkdir(parents=True, exist_ok=True)

settings = load_json(SETTINGS_PATH)
settings.update(
    {
        "language": settings.get("language", "en"),
        "agent": "CodeActAgent",
        "confirmation_mode": False,
        "llm_model": "openai/gpt-5.1-codex-max",
        "llm_api_key": "chatmock",
        "llm_base_url": "http://chatmock:5000/v1",
        "enable_default_condenser": True,
        "enable_sound_notifications": False,
        "enable_proactive_conversation_starters": False,
        "enable_solvability_analysis": False,
        "user_consents_to_analytics": False,
        "sandbox_base_container_image": RUNTIME_IMAGE,
        "sandbox_runtime_container_image": RUNTIME_IMAGE,
        "search_api_key": None,
        "sandbox_api_key": None,
        "condenser_max_size": 240,
        "git_user_name": "openhands",
        "git_user_email": "openhands@all-hands.dev",
        "v1_enabled": True,
    }
)

shttp_servers = [
    {"url": "http://context7-mcp:3000/mcp", "api_key": None, "timeout": 60},
    {"url": "http://memory-mcp:8000/mcp", "api_key": None, "timeout": 60},
]
if CONTEXT7_API_KEY:
    shttp_servers[0]["api_key"] = CONTEXT7_API_KEY

settings["mcp_config"] = {
    "sse_servers": [],
    "stdio_servers": [],
    "shttp_servers": shttp_servers,
}

mcp_json = load_json(MCP_PATH)
mcp_servers = mcp_json.get("mcpServers", {})
mcp_servers["context7"] = {
    "transport": "http",
    "url": "http://context7-mcp:3000/mcp",
    "enabled": True,
}
if CONTEXT7_API_KEY:
    mcp_servers["context7"]["headers"] = {"context7-api-key": CONTEXT7_API_KEY}
mcp_servers["memory"] = {
    "transport": "http",
    "url": "http://memory-mcp:8000/mcp",
    "enabled": True,
}
mcp_json["mcpServers"] = mcp_servers

write_json(SETTINGS_PATH, settings)
write_json(MCP_PATH, mcp_json)
