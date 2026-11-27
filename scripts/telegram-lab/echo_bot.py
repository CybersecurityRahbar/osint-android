#!/usr/bin/env python3
"""Minimal Telegram Bot API echo endpoint used only for synthetic CI messages."""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

TOKEN = os.environ.get("TELEGRAM_CI_ECHO_BOT_TOKEN", "").strip()
EXPECTED_USERNAME = os.environ.get(
    "TELEGRAM_CI_EXPECTED_BOT_USERNAME", ""
).strip().lstrip("@").lower()

if not TOKEN:
    raise SystemExit("TELEGRAM_CI_ECHO_BOT_TOKEN is required.")

BASE = "https://api.telegram.org/bot" + TOKEN + "/"


def call(method: str, payload: dict | None = None, timeout: int = 35) -> dict:
    data = urllib.parse.urlencode(payload or {}).encode()
    request = urllib.request.Request(BASE + method, data=data)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        result = json.loads(response.read().decode("utf-8"))
    if not result.get("ok"):
        raise RuntimeError("Telegram Bot API returned a non-ok response for " + method)
    return result


def main() -> None:
    identity = call("getMe").get("result", {})
    actual = str(identity.get("username", "")).lower()
    if EXPECTED_USERNAME and actual != EXPECTED_USERNAME:
        raise SystemExit("The supplied bot username does not match the configured bot token.")

    call("deleteWebhook", {"drop_pending_updates": "true"})
    print("CI echo bot ready: @" + actual, flush=True)

    offset = 0
    while True:
        try:
            updates = call(
                "getUpdates",
                {
                    "offset": str(offset),
                    "timeout": "25",
                    "allowed_updates": json.dumps(["message"]),
                },
                timeout=35,
            ).get("result", [])

            for update in updates:
                offset = max(offset, int(update.get("update_id", 0)) + 1)
                message = update.get("message", {})
                chat = message.get("chat", {})
                text = message.get("text", "")

                # Only private, synthetic CI markers are echoed. All other messages are ignored.
                if chat.get("type") != "private" or not isinstance(text, str):
                    continue
                if not text.startswith("OSINT_CI_PING_") or len(text) > 100:
                    continue

                call(
                    "sendMessage",
                    {
                        "chat_id": str(chat.get("id")),
                        "text": "OSINT_CI_ECHO_OK:" + text,
                        "reply_to_message_id": str(message.get("message_id", "")),
                    },
                )
        except urllib.error.HTTPError as error:
            print(
                "Bot API HTTP error " + str(error.code) + "; retrying.",
                file=sys.stderr,
                flush=True,
            )
            time.sleep(5)
        except Exception as error:
            print(
                "Bot poll temporarily unavailable (" + type(error).__name__ + "); retrying.",
                file=sys.stderr,
                flush=True,
            )
            time.sleep(5)


if __name__ == "__main__":
    main()
