# Telegram Session CI Lab

This workflow provisions an authenticated Android Emulator profile and runs a real Android WebView round-trip test. It is not a Playwright-only simulation: the cached state is the emulator user-data disk, so Android Keystore and the app's own WebView profile are restored together.

## Required repository secret

Create a random 32-byte key locally:

    openssl rand -hex 32

Open GitHub repository Settings → Secrets and variables → Actions → New repository secret and add:

- Name: TELEGRAM_CI_SESSION_KEY
- Value: the 64-character hexadecimal value from openssl

Keep this key unchanged. It encrypts the emulator disk image using AES-256-GCM. The raw image and the login session are never committed or uploaded as a plain artifact. Only authenticated ciphertext is stored in the repository-scoped Actions cache.

Keep the existing preview signing secrets unchanged:

- PREVIEW_KEYSTORE_BASE64
- PREVIEW_KEYSTORE_PASSWORD

## First Telegram login — bootstrap

1. Open Actions → Telegram Session CI Lab → Run workflow.
2. Select mode bootstrap.
3. Enter an email address that can receive a one-time PIN in access_email.
4. Open the protected console URL shown in the workflow summary and enter the PIN sent to that email.
5. The page displays the Android Emulator running the preview app, with the Telegram Web session window opened.
6. Log in using a dedicated Telegram test account. Approve the QR session from the Telegram app or use another official login method offered by Telegram.
7. Wait until Telegram chats are visibly loaded, then press “حفظ الجلسة وإنهاء” on the remote console page.

The workflow stops the emulator, encrypts its complete userdata image (including Android Keystore and WebView storage), and saves the ciphertext in Actions cache. It does not copy the account password, one-time code, QR image, cookies, or raw session data into GitHub.

Use a dedicated test account, not your personal primary Telegram account. The new login creates a Telegram device session; you can review and revoke it later from Telegram's Devices settings.

If the QR is displayed on the same phone where Telegram is installed, open the remote console on a laptop or a second device and scan the QR using the test account's phone. Never share the protected console URL or QR image.

## Create a private echo bot for live testing

The end-to-end test uses a bot you own, not a third-party people-search bot:

1. In Telegram, open @BotFather and create a new bot.
2. Save its bot token as an Actions secret named TELEGRAM_CI_ECHO_BOT_TOKEN.
3. Note the bot username without the @ symbol.
4. In the test Telegram account used during bootstrap, open that bot once. The app's test driver can click Telegram's Start control if Telegram still shows it.
5. The CI echo service replies only to private messages starting with OSINT_CI_PING_. It sends back OSINT_CI_ECHO_OK: followed by the exact marker.

The Android test sends a synthetic marker generated from the GitHub run ID. It sends no phone numbers, personal names, or data about third parties.

## Run the real Android WebView test

1. Open Actions → Telegram Session CI Lab → Run workflow.
2. Select mode test.
3. Enter the echo bot username in echo_bot_username.
4. GitHub Actions restores the encrypted emulator image, builds and installs the current signed APK, starts your echo bot, and runs TelegramCiLiveAndroidTest.
5. PASS requires all of the following: session verification, target-chat identity verification, opening the composer, sending one direct-message marker, detecting the outgoing bubble, and capturing the new incoming echo reply.

The test invokes the same TelegramSessionController and TelegramWeb code as the app's private-source search. It exercises DIRECT_MESSAGE (send one value without a command or prompt). Existing command-based flows and the legacy VALUE_ONLY setting remain supported.

The workflow intentionally does not send personal-lookup queries to @tellwhoobot. The generic direct-message feature is testable with an owner-controlled echo bot and synthetic values without processing a third party's personal data.

## Cache retention and re-login

A scheduled job accesses the encrypted session cache daily so normal inactivity retention does not expire it. GitHub Actions cache is not archival storage and can still be evicted by GitHub quotas or policy. Telegram may independently revoke or expire the session. If the cache disappears or Telegram requests login again, run bootstrap again.

## Security notes

- Do not place passwords, login codes, QR screenshots, cookies, session tokens, or unencrypted emulator images in source control, issues, artifacts, or workflow logs.
- Do not change TELEGRAM_CI_SESSION_KEY after bootstrap unless you have first decrypted and re-encrypted the saved emulator image.
- The remote VNC service binds to localhost on the runner. Cloudflare Quick Tunnel restricts the temporary console to the email entered for the run via a one-time PIN.
- The encrypted session cache is a convenience for repeatable CI testing, not a guarantee of permanent Telegram authentication.
