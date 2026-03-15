import json
import os
from typing import Any, Dict, List

import requests
from flask import Flask, Response, current_app, jsonify, make_response, request

from chatmock.config import BASE_INSTRUCTIONS, GPT5_CODEX_INSTRUCTIONS
from chatmock.http import build_cors_headers
from chatmock.limits import record_rate_limits_from_response
from chatmock.reasoning import (
    allowed_efforts_for_model,
    build_reasoning_param,
    extract_reasoning_from_model_name,
)
from chatmock.routes_openai import _wrap_stream_logging
from chatmock.upstream import start_upstream_request
from chatmock.utils import convert_chat_messages_to_responses_input


TARGET_MODEL = os.getenv("CHATMOCK_MODEL", "gpt-5.1-codex-max").strip() or "gpt-5.1-codex-max"
VERBOSE = os.getenv("CHATMOCK_VERBOSE", "false").strip().lower() in {"1", "true", "yes", "on"}
REASONING_EFFORT = os.getenv("CHATMOCK_REASONING_EFFORT", "low").strip() or "low"
REASONING_SUMMARY = os.getenv("CHATMOCK_REASONING_SUMMARY", "none").strip() or "none"
DEFAULT_WEB_SEARCH = os.getenv("CHATMOCK_DEFAULT_WEB_SEARCH", "false").strip().lower() in {"1", "true", "yes", "on"}
BACKEND_BASE = os.getenv("CHATMOCK_BACKEND_BASE", "http://chatmock:8000").rstrip("/")


app = Flask(__name__)
app.config.update(
    VERBOSE=VERBOSE,
    REASONING_EFFORT=REASONING_EFFORT,
    REASONING_SUMMARY=REASONING_SUMMARY,
    DEFAULT_WEB_SEARCH=DEFAULT_WEB_SEARCH,
)


def _extract_text_parts(item: Dict[str, Any]) -> List[str]:
    parts: List[str] = []
    for content in item.get("content") or []:
        if not isinstance(content, dict):
            continue
        if content.get("type") in ("input_text", "text") and isinstance(content.get("text"), str):
            parts.append(content.get("text") or "")
    return [part for part in parts if part]


def _log_json(prefix: str, payload: Any) -> None:
    try:
        print(f"{prefix}\n{json.dumps(payload, indent=2, ensure_ascii=False)}")
    except Exception:
        try:
            print(f"{prefix}\n{payload}")
        except Exception:
            pass


def _instructions_for_model(model: str) -> str:
    if "codex" in (model or "").lower():
        codex = GPT5_CODEX_INSTRUCTIONS
        if isinstance(codex, str) and codex.strip():
            return codex
    return BASE_INSTRUCTIONS


def _apply_cors(resp: Response) -> Response:
    for key, value in build_cors_headers().items():
        resp.headers.setdefault(key, value)
    return resp


def _preflight_response() -> Response:
    return _apply_cors(make_response("", 204))


def _proxy_to_backend(path: str) -> Response:
    if request.method == "OPTIONS":
        return _preflight_response()

    target_url = f"{BACKEND_BASE}{path}"
    headers = {
        key: value
        for key, value in request.headers.items()
        if key.lower() not in {"host", "content-length", "connection"}
    }
    try:
        upstream = requests.request(
            method=request.method,
            url=target_url,
            params=request.args,
            data=request.get_data(cache=True),
            headers=headers,
            timeout=600,
        )
    except requests.RequestException as exc:
        resp = make_response(jsonify({"error": {"message": f"ChatMock backend unavailable: {exc}"}}), 502)
        return _apply_cors(resp)

    resp = make_response(upstream.content, upstream.status_code)
    for key, value in upstream.headers.items():
        if key.lower() in {"content-length", "connection", "transfer-encoding", "content-encoding"}:
            continue
        resp.headers[key] = value
    return _apply_cors(resp)


@app.route("/v1/models", methods=["GET", "OPTIONS"])
def proxy_models() -> Response:
    return _proxy_to_backend("/v1/models")


@app.route("/v1/chat/completions", methods=["POST", "OPTIONS"])
def proxy_chat_completions() -> Response:
    return _proxy_to_backend("/v1/chat/completions")


@app.route("/v1/completions", methods=["POST", "OPTIONS"])
def proxy_completions() -> Response:
    return _proxy_to_backend("/v1/completions")


@app.route("/v1/responses", methods=["POST", "OPTIONS"])
def responses() -> Response:
    if request.method == "OPTIONS":
        return _preflight_response()

    verbose = bool(current_app.config.get("VERBOSE"))
    reasoning_effort = current_app.config.get("REASONING_EFFORT", "medium")
    reasoning_summary = current_app.config.get("REASONING_SUMMARY", "auto")

    raw = request.get_data(cache=True, as_text=True) or ""
    if verbose:
        try:
            print("IN POST /v1/responses\n" + raw)
        except Exception:
            pass

    try:
        payload = json.loads(raw) if raw else {}
    except Exception:
        err = {"error": {"message": "Invalid JSON body"}}
        if verbose:
            _log_json("OUT POST /v1/responses", err)
        return _apply_cors(make_response(jsonify(err), 400))

    requested_model = payload.get("model")
    model = TARGET_MODEL

    input_items = payload.get("input")
    if isinstance(input_items, str):
        input_items = [
            {
                "type": "message",
                "role": "user",
                "content": [{"type": "input_text", "text": input_items}],
            }
        ]
    elif input_items is None and isinstance(payload.get("messages"), list):
        input_items = convert_chat_messages_to_responses_input(payload.get("messages"))
    elif input_items is None and isinstance(payload.get("prompt"), str):
        input_items = [
            {
                "type": "message",
                "role": "user",
                "content": [{"type": "input_text", "text": payload.get("prompt") or ""}],
            }
        ]

    if not isinstance(input_items, list):
        err = {"error": {"message": "Request must include input: []"}}
        if verbose:
            _log_json("OUT POST /v1/responses", err)
        return _apply_cors(make_response(jsonify(err), 400))

    tools = payload.get("tools") if isinstance(payload.get("tools"), list) else None
    if tools is None and isinstance(payload.get("responses_tools"), list):
        tools = payload.get("responses_tools")

    if tools is None and bool(current_app.config.get("DEFAULT_WEB_SEARCH")):
        responses_tool_choice = payload.get("responses_tool_choice")
        if not (isinstance(responses_tool_choice, str) and responses_tool_choice == "none"):
            tools = [{"type": "web_search"}]

    tool_choice = payload.get("tool_choice")
    if tool_choice is None:
        tool_choice = payload.get("responses_tool_choice", "auto")
    parallel_tool_calls = bool(payload.get("parallel_tool_calls", False))
    stream_req = bool(payload.get("stream", False))

    model_reasoning = extract_reasoning_from_model_name(TARGET_MODEL)
    reasoning_overrides = payload.get("reasoning") if isinstance(payload.get("reasoning"), dict) else model_reasoning
    reasoning_param = build_reasoning_param(
        reasoning_effort,
        reasoning_summary,
        reasoning_overrides,
        allowed_efforts=allowed_efforts_for_model(model),
    )

    instructions = payload.get("instructions")
    if isinstance(instructions, str) and instructions.strip():
        instructions = instructions.strip()
    else:
        system_texts: List[str] = []
        filtered_input_items: List[Dict[str, Any]] = []
        for item in input_items:
            if (
                isinstance(item, dict)
                and item.get("type") == "message"
                and item.get("role") == "system"
            ):
                system_texts.extend(_extract_text_parts(item))
                continue
            filtered_input_items.append(item)
        if system_texts:
            instructions = "\n\n".join(system_texts)
            input_items = filtered_input_items
        else:
            instructions = _instructions_for_model(model)

    upstream, error_resp = start_upstream_request(
        model,
        input_items,
        instructions=instructions,
        tools=tools,
        tool_choice=tool_choice,
        parallel_tool_calls=parallel_tool_calls,
        reasoning_param=reasoning_param,
    )
    if error_resp is not None:
        if verbose:
            try:
                body = error_resp.get_data(as_text=True)
                if body:
                    try:
                        parsed = json.loads(body)
                    except Exception:
                        parsed = body
                    _log_json("OUT POST /v1/responses", parsed)
            except Exception:
                pass
        return _apply_cors(error_resp)

    record_rate_limits_from_response(upstream)

    if upstream.status_code >= 400:
        try:
            raw_err = upstream.content
            err_body = json.loads(raw_err.decode("utf-8", errors="ignore")) if raw_err else {"raw": upstream.text}
        except Exception:
            err_body = {"raw": upstream.text}
        err = {"error": {"message": (err_body.get("error", {}) or {}).get("message", "Upstream error")}}
        if verbose:
            _log_json("OUT POST /v1/responses", err)
        return _apply_cors(make_response(jsonify(err), upstream.status_code))

    if stream_req:
        if verbose:
            print("OUT POST /v1/responses (streaming response)")
        stream_iter = _wrap_stream_logging(
            "STREAM OUT /v1/responses",
            upstream.iter_content(chunk_size=None),
            verbose,
        )
        resp = Response(
            stream_iter,
            status=upstream.status_code,
            mimetype="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )
        return _apply_cors(resp)

    final_response = None
    last_response = None
    error_message = None
    try:
        for raw_line in upstream.iter_lines(decode_unicode=False):
            if not raw_line:
                continue
            line = raw_line.decode("utf-8", errors="ignore") if isinstance(raw_line, (bytes, bytearray)) else raw_line
            if not line.startswith("data: "):
                continue
            data = line[len("data: "):].strip()
            if not data:
                continue
            if data == "[DONE]":
                break
            try:
                evt = json.loads(data)
            except Exception:
                continue
            if isinstance(evt.get("response"), dict):
                last_response = evt.get("response")
            kind = evt.get("type")
            if kind == "response.completed":
                final_response = evt.get("response") or last_response
                break
            if kind == "response.failed":
                final_response = evt.get("response") or last_response
                error_message = ((evt.get("response") or {}).get("error") or {}).get("message", "response.failed")
                break
    finally:
        upstream.close()

    if not isinstance(final_response, dict):
        err = {"error": {"message": error_message or "Upstream response stream ended before response.completed"}}
        if verbose:
            _log_json("OUT POST /v1/responses", err)
        return _apply_cors(make_response(jsonify(err), 502))

    if isinstance(requested_model, str) and requested_model.strip():
        final_response["model"] = requested_model

    if verbose:
        _log_json("OUT POST /v1/responses", final_response)

    return _apply_cors(make_response(jsonify(final_response), upstream.status_code))
