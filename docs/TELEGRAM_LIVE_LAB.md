# Telegram Live Lab — OSINT Workbench

This is an on-device end-to-end test harness for the Telegram Web integration. It runs inside the Android app because the authenticated Telegram WebView profile is stored in the app's private storage on the user's phone.

## Open the lab

Install the stable-signed Preview APK, then open:

**Settings → مصادر Telegram الخاصة → مختبر Telegram الحي**

The lab is separate from the normal private-source registry. Its test profile is saved locally in SharedPreferences (telegram_live_lab) and is not uploaded to GitHub.

## Login setup

1. Open Telegram Web from the lab.
2. Complete the normal Telegram Web login flow on the device only if Telegram asks for it.
3. Return to the app with Android Back or the Telegram window's close control.
4. Press 1 · فحص الجلسة الحالية.

The login UI uses the same retained SessionWebView as the normal private-source search. Do not clear app storage, cookies, Local Storage, IndexedDB, or sign out as part of this test.

There are intentionally no fields for Telegram passwords, one-time login codes, QR payloads, session strings, cookies, api_id, or api_hash. Do not place Telegram account credentials or exported session data in the repository, GitHub Actions Secrets, issue reports, or PROJECT_CONTEXT.md. No TDLib or second Telegram client is involved.

## Test profile fields

Configure a bot that you own or are explicitly authorized to test:

| Field | Meaning |
| --- | --- |
| Bot name | Friendly local label |
| Bot username | Telegram @username, without @ |
| Expected chat title | Optional visible title used for peer verification |
| Test path | Number-style or name-style controller path |
| Flow | Command + prompt, command + value, or value only |
| Command | Bot command when the selected flow uses one |
| Prompt | Expected prompt when using command + prompt |
| Test value | Synthetic payload for the test bot |
| Expected reply | Optional marker that should appear in the bot's reply |

Use an echo/test bot and a synthetic value such as OSINT_LAB_PING_2026. Do not send phone numbers, names, or other personal data belonging to another person.

## Test sequence

1. Session probe: reads the live WebView and runs the same 15-second session verification used by the private-source engine. The report includes WebView host/load state and DOM selector match counts, not message text or credentials.
2. Full live flow: opens the configured bot, verifies the target identity and composer, follows the selected command/prompt flow, sends the synthetic test value once, checks its outgoing echo, and waits for new incoming bot replies.
3. Result: LIVE PASS means the controller completed and captured a new reply; LIVE PARTIAL means a reply arrived but the configured expected marker was absent; LIVE FAIL reports the stage that stopped.

The test uses the same TelegramSessionRuntime.controller as regular searches. It does not bypass session checks, skip peer identity verification, or retry ambiguous sends.

## What CI can and cannot prove

GitHub Actions can run unit tests and build the signed APK. A hosted emulator does not have the logged-in Telegram Web profile from the user's phone, so CI cannot honestly claim to have completed the authenticated Telegram session test. The actual live flow must be run from this in-app lab on the device where the Telegram Web session exists.

The existing preview signing secrets remain unchanged:

- PREVIEW_KEYSTORE_BASE64
- PREVIEW_KEYSTORE_PASSWORD

Those are APK signing secrets only. No Telegram account secret is required in GitHub Actions.

## Safe report to share

Share the lab's stage trace and safe DOM/session diagnostics. Do not share Telegram message contents, phone numbers, names, login codes, passwords, cookies, or exported session/profile files. Include the Android System WebView version when reporting a failure.
