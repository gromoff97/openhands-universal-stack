from pathlib import Path
import textwrap


PATH = Path("/src/chatmock/routes_openai.py")
MARKER = '\n\n@openai_bp.route("/v1/models", methods=["GET"])\ndef list_models() -> Response:\n'
ROUTE_DECL = '@openai_bp.route("/v1/responses", methods=["POST"])'


SHIM = textwrap.dedent(
    """

    @openai_bp.route("/v1/responses", methods=["POST"])
    def responses() -> Response:
        verbose = bool(current_app.config.get("VERBOSE"))
        debug_model = current_app.config.get("DEBUG_MODEL")
        reasoning_effort = current_app.config.get("REASONING_EFFORT", "medium")
        reasoning_summary = current_app.config.get("REASONING_SUMMARY", "auto")

        raw = request.get_data(cache=True, as_text=True) or ""
        if verbose:
            try:
                print("IN POST /v1/responses\\n" + raw)
            except Exception:
                pass

        try:
            payload = json.loads(raw) if raw else {}
        except Exception:
            err = {"error": {"message": "Invalid JSON body"}}
            if verbose:
                _log_json("OUT POST /v1/responses", err)
            return jsonify(err), 400

        requested_model = payload.get("model")
        model = normalize_model_name(requested_model, debug_model)

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
            return jsonify(err), 400

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

        model_reasoning = extract_reasoning_from_model_name(requested_model)
        reasoning_overrides = payload.get("reasoning") if isinstance(payload.get("reasoning"), dict) else model_reasoning
        reasoning_param = build_reasoning_param(
            reasoning_effort,
            reasoning_summary,
            reasoning_overrides,
            allowed_efforts=allowed_efforts_for_model(model),
        )

        instructions = payload.get("instructions")
        if not (isinstance(instructions, str) and instructions.strip()):
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
            return error_resp

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
            return jsonify(err), upstream.status_code

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
            for k, v in build_cors_headers().items():
                resp.headers.setdefault(k, v)
            return resp

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
            resp = make_response(jsonify(err), 502)
            for k, v in build_cors_headers().items():
                resp.headers.setdefault(k, v)
            return resp

        if requested_model and not isinstance(final_response.get("model"), str):
            final_response["model"] = requested_model

        if verbose:
            _log_json("OUT POST /v1/responses", final_response)

        resp = make_response(jsonify(final_response), upstream.status_code)
        for k, v in build_cors_headers().items():
            resp.headers.setdefault(k, v)
        return resp
    """
)


def main() -> None:
    text = PATH.read_text()
    if ROUTE_DECL in text:
        return
    if MARKER not in text:
        raise SystemExit("Unable to find insertion point for ChatMock responses shim")
    PATH.write_text(text.replace(MARKER, SHIM + MARKER))


if __name__ == "__main__":
    main()
