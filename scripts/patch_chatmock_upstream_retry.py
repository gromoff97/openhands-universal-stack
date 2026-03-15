from pathlib import Path
import re


PATH = Path("/src/chatmock/upstream.py")
PATTERN = re.compile(
    r"""    try:\n"""
    r"""        upstream = requests\.post\(\n"""
    r"""            CHATGPT_RESPONSES_URL,\n"""
    r"""            headers=headers,\n"""
    r"""            json=responses_payload,\n"""
    r"""            stream=True,\n"""
    r"""            timeout=600,\n"""
    r"""        \)\n"""
    r"""    except requests\.RequestException as e:\n"""
    r"""        resp = make_response\(jsonify\(\{"error": \{"message": f"Upstream ChatGPT request failed: \{e\}"\}\}\), 502\)\n"""
    r"""        for k, v in build_cors_headers\(\)\.items\(\):\n"""
    r"""            resp\.headers\.setdefault\(k, v\)\n"""
    r"""        return None, resp\n"""
    r"""    return upstream, None\n""",
    re.MULTILINE,
)


REPLACEMENT = """    max_attempts = max(1, int(current_app.config.get("UPSTREAM_REQUEST_RETRIES", 3) or 3))\n    retry_backoff_seconds = float(current_app.config.get("UPSTREAM_REQUEST_RETRY_BACKOFF_SECONDS", 1.5) or 1.5)\n    last_error = None\n\n    for attempt in range(1, max_attempts + 1):\n        try:\n            upstream = requests.post(\n                CHATGPT_RESPONSES_URL,\n                headers=headers,\n                json=responses_payload,\n                stream=True,\n                timeout=600,\n            )\n            return upstream, None\n        except requests.RequestException as e:\n            last_error = e\n            if verbose:\n                print(\n                    f"Upstream ChatGPT request attempt {attempt}/{max_attempts} failed: {e}"\n                )\n            if attempt >= max_attempts:\n                break\n            time.sleep(retry_backoff_seconds * attempt)\n\n    resp = make_response(\n        jsonify({"error": {"message": f"Upstream ChatGPT request failed: {last_error}"}}),\n        502,\n    )\n    for k, v in build_cors_headers().items():\n        resp.headers.setdefault(k, v)\n    return None, resp\n"""


def main() -> None:
    text = PATH.read_text()
    if "UPSTREAM_REQUEST_RETRIES" in text:
        return
    new_text, count = PATTERN.subn(REPLACEMENT, text, count=1)
    if count != 1:
        raise SystemExit("Unable to find upstream request block for retry patch")
    PATH.write_text(new_text)


if __name__ == "__main__":
    main()
