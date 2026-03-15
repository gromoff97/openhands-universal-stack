import json
import time
import uuid
from typing import Any, Dict, Iterable, List, Optional

import requests
from flask import Flask, Response, jsonify, make_response, request


BACKEND_BASE = (__import__("os").environ.get("CHATMOCK_BACKEND_BASE") or "http://chatmock-backend:8000").rstrip("/")
REQUEST_TIMEOUT = 600

app = Flask(__name__)


def _cors_headers() -> Dict[str, str]:
    origin = request.headers.get("Origin", "*")
    return {
        "Access-Control-Allow-Origin": origin,
        "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Requested-With, x-stainless-lang, x-stainless-package-version, x-stainless-os, x-stainless-arch, x-stainless-runtime, x-stainless-runtime-version, OpenAI-Beta",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Credentials": "true",
        "Vary": "Origin",
    }


def _apply_cors(resp: Response) -> Response:
    for key, value in _cors_headers().items():
        resp.headers.setdefault(key, value)
    return resp


def _preflight() -> Response:
    return _apply_cors(make_response("", 204))


def _proxy(path: str) -> Response:
    if request.method == "OPTIONS":
        return _preflight()

    headers = {
        key: value
        for key, value in request.headers.items()
        if key.lower() not in {"host", "content-length", "connection"}
    }
    try:
        upstream = requests.request(
            method=request.method,
            url=f"{BACKEND_BASE}{path}",
            params=request.args,
            data=request.get_data(cache=True),
            headers=headers,
            timeout=REQUEST_TIMEOUT,
        )
    except requests.RequestException as exc:
        return _apply_cors(make_response(jsonify({"error": {"message": f"Backend unavailable: {exc}"}}), 502))

    resp = make_response(upstream.content, upstream.status_code)
    for key, value in upstream.headers.items():
        if key.lower() in {"content-length", "connection", "transfer-encoding", "content-encoding"}:
            continue
        resp.headers[key] = value
    return _apply_cors(resp)


def _extract_text_parts(content: Any) -> str:
    if isinstance(content, str):
        return content
    parts: List[str] = []
    if isinstance(content, list):
        for item in content:
            if not isinstance(item, dict):
                continue
            if item.get("type") in {"input_text", "output_text", "text"} and isinstance(item.get("text"), str):
                parts.append(item["text"])
            elif item.get("type") == "refusal" and isinstance(item.get("refusal"), str):
                parts.append(item["refusal"])
    return "\n".join(part for part in parts if part).strip()


def _responses_input_to_messages(payload: Dict[str, Any]) -> List[Dict[str, Any]]:
    messages: List[Dict[str, Any]] = []

    instructions = payload.get("instructions")
    if isinstance(instructions, str) and instructions.strip():
        messages.append({"role": "system", "content": instructions.strip()})

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
        for item in payload["messages"]:
            if not isinstance(item, dict):
                continue
            role = item.get("role") or "user"
            content = _extract_text_parts(item.get("content"))
            if content:
                messages.append({"role": role, "content": content})
        return messages
    elif input_items is None and isinstance(payload.get("prompt"), str):
        messages.append({"role": "user", "content": payload["prompt"]})
        return messages

    if not isinstance(input_items, list):
        return messages

    for item in input_items:
        if not isinstance(item, dict):
            continue
        item_type = item.get("type")
        if item_type == "message":
            role = item.get("role") or "user"
            text = _extract_text_parts(item.get("content"))
            if text:
                messages.append({"role": role, "content": text})
        elif item_type == "function_call_output":
            tool_output = item.get("output")
            if not isinstance(tool_output, str):
                tool_output = json.dumps(tool_output, ensure_ascii=False)
            messages.append(
                {
                    "role": "tool",
                    "tool_call_id": item.get("call_id") or item.get("id") or f"tool_{uuid.uuid4().hex[:8]}",
                    "content": tool_output,
                }
            )

    return messages


def _build_chat_request(payload: Dict[str, Any]) -> Dict[str, Any]:
    messages = _responses_input_to_messages(payload)
    if not messages:
        raise ValueError("Request must include input/messages/prompt")

    body: Dict[str, Any] = {
        "model": payload.get("model") or "openai/gpt-5.1-codex-max",
        "messages": messages,
        "stream": bool(payload.get("stream", False)),
    }

    for key in ("tools", "tool_choice", "parallel_tool_calls", "responses_tools", "responses_tool_choice", "stream_options"):
        if key in payload:
            body[key] = payload[key]

    return body


def _message_text_and_tool_calls(message: Dict[str, Any]) -> tuple[str, List[Dict[str, Any]]]:
    text = ""
    if isinstance(message.get("content"), str):
        text = message["content"]
    elif isinstance(message.get("content"), list):
        text = _extract_text_parts(message.get("content"))
    tool_calls = message.get("tool_calls") if isinstance(message.get("tool_calls"), list) else []
    return text, tool_calls


def _chat_completion_to_response(payload: Dict[str, Any], chat_data: Dict[str, Any]) -> Dict[str, Any]:
    requested_model = payload.get("model") or chat_data.get("model") or "openai/gpt-5.1-codex-max"
    created_at = int(chat_data.get("created") or time.time())
    response_id = f"resp_{uuid.uuid4().hex}"
    choices = chat_data.get("choices") if isinstance(chat_data.get("choices"), list) else []
    first_choice = choices[0] if choices else {}
    message = first_choice.get("message") if isinstance(first_choice, dict) else {}
    if not isinstance(message, dict):
        message = {}

    text, tool_calls = _message_text_and_tool_calls(message)
    output_items: List[Dict[str, Any]] = []

    content_items: List[Dict[str, Any]] = []
    if text:
        content_items.append({"type": "output_text", "text": text, "annotations": []})
    if content_items:
        output_items.append(
            {
                "id": f"msg_{uuid.uuid4().hex[:24]}",
                "type": "message",
                "role": "assistant",
                "status": "completed",
                "content": content_items,
            }
        )

    for tool_call in tool_calls:
        if not isinstance(tool_call, dict):
            continue
        function = tool_call.get("function") if isinstance(tool_call.get("function"), dict) else {}
        output_items.append(
            {
                "id": tool_call.get("id") or f"fc_{uuid.uuid4().hex[:24]}",
                "type": "function_call",
                "call_id": tool_call.get("id") or f"call_{uuid.uuid4().hex[:24]}",
                "name": function.get("name") or "",
                "arguments": function.get("arguments") or "",
                "status": "completed",
            }
        )

    usage = chat_data.get("usage") if isinstance(chat_data.get("usage"), dict) else {}

    return {
        "id": response_id,
        "object": "response",
        "created_at": created_at,
        "status": "completed",
        "model": requested_model,
        "output": output_items,
        "output_text": text,
        "usage": {
            "input_tokens": int(usage.get("prompt_tokens") or 0),
            "output_tokens": int(usage.get("completion_tokens") or 0),
            "total_tokens": int(usage.get("total_tokens") or 0),
        },
    }


def _stream_chat_to_responses(payload: Dict[str, Any], upstream: requests.Response) -> Iterable[bytes]:
    requested_model = payload.get("model") or "openai/gpt-5.1-codex-max"
    response_id = f"resp_{uuid.uuid4().hex}"
    created_at = int(time.time())
    full_text = ""
    usage: Dict[str, int] = {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0}
    tool_calls: Dict[int, Dict[str, Any]] = {}

    created_event = {
        "type": "response.created",
        "response": {
            "id": response_id,
            "object": "response",
            "created_at": created_at,
            "status": "in_progress",
            "model": requested_model,
        },
    }
    yield f"data: {json.dumps(created_event, ensure_ascii=False)}\n\n".encode()

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
            event = json.loads(data)
        except Exception:
            continue

        if isinstance(event.get("usage"), dict):
            usage = {
                "input_tokens": int(event["usage"].get("prompt_tokens") or 0),
                "output_tokens": int(event["usage"].get("completion_tokens") or 0),
                "total_tokens": int(event["usage"].get("total_tokens") or 0),
            }

        choices = event.get("choices") if isinstance(event.get("choices"), list) else []
        if not choices:
            continue
        delta = choices[0].get("delta") if isinstance(choices[0], dict) else {}
        if not isinstance(delta, dict):
            continue

        content_piece = delta.get("content")
        if isinstance(content_piece, str) and content_piece:
            full_text += content_piece
            yield f"data: {json.dumps({'type': 'response.output_text.delta', 'delta': content_piece, 'output_index': 0, 'content_index': 0}, ensure_ascii=False)}\n\n".encode()

        if isinstance(delta.get("tool_calls"), list):
            for tool_call in delta["tool_calls"]:
                if not isinstance(tool_call, dict):
                    continue
                index = int(tool_call.get("index") or 0)
                state = tool_calls.setdefault(
                    index,
                    {
                        "id": tool_call.get("id") or f"fc_{uuid.uuid4().hex[:24]}",
                        "call_id": tool_call.get("id") or f"call_{uuid.uuid4().hex[:24]}",
                        "name": "",
                        "arguments": "",
                    },
                )
                function = tool_call.get("function") if isinstance(tool_call.get("function"), dict) else {}
                if isinstance(function.get("name"), str) and function.get("name"):
                    state["name"] = function["name"]
                if isinstance(function.get("arguments"), str):
                    state["arguments"] += function["arguments"]

    output: List[Dict[str, Any]] = []
    if full_text:
        output.append(
            {
                "id": f"msg_{uuid.uuid4().hex[:24]}",
                "type": "message",
                "role": "assistant",
                "status": "completed",
                "content": [{"type": "output_text", "text": full_text, "annotations": []}],
            }
        )
    for state in tool_calls.values():
        output.append(
            {
                "id": state["id"],
                "type": "function_call",
                "call_id": state["call_id"],
                "name": state["name"],
                "arguments": state["arguments"],
                "status": "completed",
            }
        )

    completed_event = {
        "type": "response.completed",
        "response": {
            "id": response_id,
            "object": "response",
            "created_at": created_at,
            "status": "completed",
            "model": requested_model,
            "output": output,
            "output_text": full_text,
            "usage": usage,
        },
    }
    yield f"data: {json.dumps(completed_event, ensure_ascii=False)}\n\n".encode()
    yield b"data: [DONE]\n\n"


@app.route("/v1/models", methods=["GET", "OPTIONS"])
def models() -> Response:
    return _proxy("/v1/models")


@app.route("/v1/chat/completions", methods=["POST", "OPTIONS"])
def chat_completions() -> Response:
    return _proxy("/v1/chat/completions")


@app.route("/v1/completions", methods=["POST", "OPTIONS"])
def completions() -> Response:
    return _proxy("/v1/completions")


@app.route("/v1/responses", methods=["POST", "OPTIONS"])
def responses() -> Response:
    if request.method == "OPTIONS":
        return _preflight()

    try:
        payload = request.get_json(force=True, silent=False) or {}
    except Exception:
        return _apply_cors(make_response(jsonify({"error": {"message": "Invalid JSON body"}}), 400))

    try:
        chat_request = _build_chat_request(payload)
    except ValueError as exc:
        return _apply_cors(make_response(jsonify({"error": {"message": str(exc)}}), 400))

    headers = {
        key: value
        for key, value in request.headers.items()
        if key.lower() not in {"host", "content-length", "connection"}
    }
    headers["Content-Type"] = "application/json"

    try:
        upstream = requests.post(
            f"{BACKEND_BASE}/v1/chat/completions",
            data=json.dumps(chat_request),
            headers=headers,
            stream=bool(chat_request.get("stream")),
            timeout=REQUEST_TIMEOUT,
        )
    except requests.RequestException as exc:
        return _apply_cors(make_response(jsonify({"error": {"message": f"Backend unavailable: {exc}"}}), 502))

    if upstream.status_code >= 400:
        try:
            body = upstream.json()
        except Exception:
            body = {"error": {"message": upstream.text or "Upstream error"}}
        return _apply_cors(make_response(jsonify(body), upstream.status_code))

    if chat_request.get("stream"):
        resp = Response(
            _stream_chat_to_responses(payload, upstream),
            status=upstream.status_code,
            mimetype="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )
        return _apply_cors(resp)

    try:
        chat_data = upstream.json()
    finally:
        upstream.close()
    resp = make_response(jsonify(_chat_completion_to_response(payload, chat_data)), upstream.status_code)
    return _apply_cors(resp)
