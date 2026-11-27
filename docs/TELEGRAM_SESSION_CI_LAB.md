# Telegram Session CI Lab

This workflow provisions an authenticated Android Emulator profile and runs a real Android WebView smoke test. It is not a Playwright-only simulation: the cached state is the emulator user-data disk, so Android Keystore and the app's own WebView profile are restored together.

## No Termux command and no new encryption secret

You do not need Termux, OpenSSL commands on your phone, or a new TELEGRAM_CI_SESSION_KEY secret.

The workflow derives a separate AES-256-GCM key inside the GitHub runner using HKDF-SHA256 from the existing stable APK signing-keystore secrets:

- PREVIEW_KEYSTORE_BASE64
- PREVIEW_KEYSTORE_PASSWORD

Those two secrets are already used to sign preview builds. The derived encryption key is only held in runner memory. Each session archive gets a fresh random salt and nonce; only the authenticated ciphertext is stored in the GitHub Actions cache. The unencrypted emulator image and login state are never committed or uploaded as a plain artifact.

Keep the signing secrets unchanged. If the preview signing keystore or its password is rotated, the previous session cache cannot be decrypted; run bootstrap again to create a new saved session.

## First Telegram login — bootstrap

1. Open Actions → Telegram Session CI Lab → Run workflow.
2. Select mode bootstrap.
3. Enter an email address that can receive a one-time PIN in access_email.
4. Open the protected console URL shown in the workflow summary and enter the PIN sent to that email.
5. The page displays the Android Emulator running the preview app, with the Telegram Web session window opened.
6. Log in using a dedicated Telegram test account. Approve the QR session from the Telegram app or use another official login method offered by Telegram.
7. Wait until Telegram chats are visibly loaded, then press “حفظ الجلسة وإنهاء” on the remote console page.

The workflow stops the emulator, encrypts its complete userdata image (including Android Keystore and WebView storage), and saves only ciphertext in Actions cache. It does not copy the account password, one-time code, QR image, cookies, or raw session data into GitHub.

Use a dedicated test account, not your personal primary Telegram account. The new login creates a Telegram device session; you can review and revoke it later from Telegram's Devices settings.

If the QR is displayed on the same phone where Telegram is installed, open the remote console on a laptop or a second device and scan the QR using the test account's phone. Never share the protected console URL or QR image.

## Username-only /start smoke test

The live test does not use a Bot API token and does not require @BotFather.

1. The workflow input telegram_bot_username defaults to tellwhoobot. You can leave it unchanged or enter another bot username that you are authorized to test.
2. The test Telegram account must have permission to open the bot. During the test, the Android app opens the public username directly inside its existing Telegram WebView.
3. The test verifies the authenticated session and the identity of the selected bot chat.
4. If Telegram displays its native Start button, the test clicks it. Otherwise, it sends exactly one /start message through the same WebView send function used by the app.
5. It verifies that the outgoing /start appears and that at least one new incoming response arrives from that chat.

The test does not send a phone number, name, lookup query, Bot API token, or other personal data. It does not store or print the bot's reply text in CI logs. This tests only login, chat navigation, UI detection, one safe start action, and reply collection.

## Run the real Android WebView test

1. Open Actions → Telegram Session CI Lab → Run workflow.
2. Select mode test.
3. Keep telegram_bot_username as tellwhoobot, or enter an authorized test bot username.
4. GitHub Actions restores the encrypted emulator image, builds and installs the current app, and runs TelegramCiLiveAndroidTest.
5. PASS requires session verification, target-chat identity verification, a single /start action, detection of its outgoing message, and capture of a new incoming response.

The test uses the actual Android app and retained Telegram WebView. The app's existing source flow settings (command + prompt, command + value, direct message, and legacy value-only) are not changed by this smoke test.

## Cache retention and re-login

A scheduled job accesses the encrypted session cache daily to reduce ordinary GitHub cache inactivity expiry. GitHub Actions cache is not archival storage and can still be evicted by quotas or policy. Accessing the cache does not guarantee that Telegram will keep a session active; Telegram may independently revoke or expire the login. If the cache disappears or Telegram requests login again, run bootstrap again.

## Security notes

- Do not place passwords, login codes, QR screenshots, cookies, session tokens, or unencrypted emulator images in source control, issues, artifacts, or workflow logs.
- The encryption key is derived from the existing signing secrets; do not publish either of those secrets or rotate them without planning a new bootstrap.
- The remote VNC service binds to localhost on the runner. Cloudflare Quick Tunnel restricts the temporary console to the email entered for the run via a one-time PIN.
- The encrypted session cache is a convenience for repeatable CI testing, not a guarantee of permanent Telegram authentication.
