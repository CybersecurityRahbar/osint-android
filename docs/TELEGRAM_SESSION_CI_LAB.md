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
4. Wait for the step “Open protected remote login console”, then copy the `PROTECTED ANDROID CONSOLE URL` printed directly in that step's live logs. Do not wait for the overall workflow summary: GitHub only publishes job summaries after the job finishes, while this bootstrap step intentionally remains active until you save the session. Open the URL in a browser and enter the PIN sent to the allowed email. The workflow masks the access email in subsequent step logs.
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


## Emulator executable path troubleshooting (2026-11-27)

The workflow now verifies that the SDK package installed the binary at `$ANDROID_HOME/emulator/emulator`, adds its directory to `GITHUB_PATH`, and invokes that binary by absolute path in both bootstrap and test modes. This avoids relying on the runner's default PATH containing the emulator package directory.


## Separation between general Android CI and the authenticated Telegram test

The normal `Android CI` workflow runs unit tests and the non-live Android instrumentation suite. It explicitly excludes:
- `ThemesSettingsNavigationAndroidTest`, because its Compose scroll test remains intentionally disabled until the UI test harness is stabilized.
- The test method marked `RequiresTelegramSession`, because it needs a restored logged-in Telegram WebView and an explicit bot username.

The dedicated `Telegram Session CI Lab` passes `-PtelegramCiBotUsername=...`. Gradle then narrows instrumentation to `TelegramCiLiveAndroidTest` only and passes that username to the test runner. This prevents ordinary Android CI from attempting a live Telegram operation without a session or a bot configuration.


## Linux host dependencies for Android Emulator (2026-11-27)

The Telegram CI job installs `libpulse0` explicitly. Although the emulator is started with `-no-audio`, the Linux QEMU binary can still dynamically load PulseAudio's client library. Before creating or booting the AVD, the workflow now:
- Runs `ldd` against the x86_64 QEMU executable and fails immediately if any dependency is marked `not found`.
- Runs the Android Emulator launcher with `-version` as a launch preflight.
- Checks that the emulator process remains alive shortly after boot is requested and prints its own log immediately if it exits.
This is intended to turn native runner-image dependency failures into a short, explicit preflight failure instead of a three-minute ADB timeout.


## Correctly resolving Android Emulator-bundled libraries

The QEMU executable is not a standalone Linux binary. The Android SDK bundles emulator-specific libraries in `emulator/lib64`, `emulator/lib64/qt/lib`, and the software-renderer directory. The normal `emulator` launcher configures `LD_LIBRARY_PATH` for these locations before it starts QEMU. Therefore a raw `ldd qemu-system-x86_64` call without that search path reports SDK-bundled libraries as `not found`, even though they exist in the SDK. The CI preflight now supplies the same SDK library paths to `ldd`; only genuinely unresolved dependencies after that are treated as errors. The system dependency `libpulse0` remains installed separately.


The general Android CI workflow also runs this host-runtime preflight now, before its instrumentation emulator tests. This provides an automated check of the same QEMU library search-path setup without requiring a manual Telegram bootstrap to discover basic host dependency issues.


## KVM hardware-acceleration permissions (2026-11-27)

The first bootstrap that passed the SDK/QEMU shared-library preflight reached the emulator launch but QEMU exited immediately with:

```text
ProbeKVM: This user doesn't have permissions to use KVM (/dev/kvm).
ERROR | x86_64 emulation currently requires hardware acceleration!
```

The runner did expose `/dev/kvm`; the issue was that the GitHub Actions runner user did not have permission to open it. The Telegram workflow now has an explicit `Enable KVM access for Android Emulator` step before the library preflight and APK build. It applies the KVM udev rule documented by GitHub, updates the current `/dev/kvm` node on this disposable hosted VM, then verifies both read/write access and a real `open(O_RDWR)` from the workflow user. If the device is absent or still inaccessible, the job fails at this short preflight with a direct error instead of trying to launch Android and failing later.

References:
- GitHub Changelog, hardware-accelerated Android virtualization in Actions: https://github.blog/changelog/2024-04-02-github-actions-hardware-accelerated-android-virtualization-now-available/
- GitHub-hosted runners reference: https://docs.github.com/en/actions/reference/runners/github-hosted-runners

This permission change is scoped to the temporary GitHub-hosted runner used by this workflow; it does not alter permissions on the user's phone or computer.


## KVM fix cross-check — Android CI #213 (2026-11-27)

After the Telegram Lab #7 diagnosis, Android CI #213 ran on the commit containing the Telegram workflow change and completed successfully. Its existing `Enable KVM for Android emulator` step passed, followed by successful Android runtime parser instrumentation, stable signing, APK build, and artifact upload. This is useful evidence that the GitHub-hosted runner's documented KVM udev setup is available in this repository's Android CI. It does not replace the required Telegram bootstrap check: the separate Telegram workflow must still pass its newly added KVM read/write/open preflight and then boot its GUI emulator.
- Run: https://github.com/CybersecurityRahbar/osint-android/actions/runs/46067211019


## noVNC screen rendering failure and root-path compatibility (2026-11-28)

The first bootstrap that successfully passed KVM and booted Android reached the protected console, but the mobile screenshot showed the noVNC fallback error page, unstyled controls, and broken icon images instead of the Android desktop. This indicates the noVNC web client did not complete its static UI/resource loading; it is not evidence that Telegram itself failed or that the Android APK failed to build.

The runner uses Ubuntu 24.04's packaged noVNC 1.3.x. Its `vnc.html` loads resources such as `app/ui.js`, `app/styles/base.css`, and `core/rfb.js` relative to the page. The protected console previously exposed noVNC only below `/novnc/`. The console now also exposes noVNC at origin-root paths (`/vnc.html`, `/app/*`, `/core/*`, `/vendor/*`, `/images/*`, `/include/*`, `/defaults.json`, `/mandatory.json`, and `/websockify`) and points the iframe to root `/vnc.html` with websocket path `websockify`. The older `/novnc/*` route remains as a compatibility alias.

Before exposing the Cloudflare URL, the workflow now verifies:
- The key noVNC HTML, JavaScript, CSS, core and defaults files are served successfully by websockify.
- The x11vnc endpoint returns an RFB protocol banner.
- Those noVNC resources also return HTTP 200 through the protected-console reverse proxy.

These checks are intended to make static-resource path errors fail inside Actions with the exact URL path, rather than present an unusable black page to the user. The current bootstrap run that exposed the screenshot uses the previous code and cannot be repaired in place; its page should not be used to log in or save a session. The next bootstrap must use the updated `main` commit and pass the new preflight checks before its link is shared.


## Android CI #214 validation of the noVNC proxy update (2026-11-28)

Android CI #214 completed successfully on commit `d94c0d712d0b3cffe6d0d0db9f9724b898e9d91a`. In particular, `Validate Telegram CI helper scripts` passed, confirming that the modified `remote_console.py` parses/compiles, and the general Android tests, signing and APK build all passed. This does not execute the new noVNC HTTP asset probes because those belong to the Telegram bootstrap workflow's protected-console step. The definitive test remains a new Telegram bootstrap on this commit.
- Run: https://github.com/CybersecurityRahbar/osint-android/actions/runs/46070705884



## Bootstrap console token argument parsing fix (2026-11-28)

Telegram Session CI Lab run #9 (Run ID `46071534803`) booted the Android emulator successfully, but the protected console process exited before opening port 8080. Its log reported:

```
remote_console.py: error: argument --finish-token: expected one argument
```

The workflow creates the finish token with Python's `secrets.token_urlsafe(32)`. Such a token may begin with a hyphen; when passed as a separate argument after `--finish-token`, argparse can interpret it as another option instead of the token value.

The workflow now passes the argument using the unambiguous `--finish-token="$FINISH_TOKEN"` form. This preserves the randomly generated token and the existing protected finish flow; no token is printed to logs. It fixes the argument-parsing failure that prevented the HTTP console from starting.

The change is committed to `main` as `70142208ba3a9231b7d65e0f342713a7fa8efc00`. It has not yet been validated by a new Telegram bootstrap run. The next run must confirm that the console starts on port 8080, the noVNC reverse-proxy asset checks pass, and the Cloudflare-protected Android screen is actually visible before Telegram login or session saving is attempted.
- Failed run: https://github.com/CybersecurityRahbar/osint-android/actions/runs/46071534803



## Bootstrap #10: root-relative noVNC asset paths were stripped by the proxy (2026-11-28)

Run #10 (Run ID `46074015993`, commit `da847fdaa598bd8f73a08cef1d39a37d416abc02`) passed the token parsing point and started the protected console on port 8080. Direct noVNC checks all passed, and x11vnc returned the expected `RFB 003.008` banner. The reverse-proxy check then failed on the second resource:

```
OK /vnc.html
noVNC reverse-proxy check failed for /app/ui.js (HTTP 404)
```

This was a separate, concrete bug in `scripts/telegram-lab/remote_console.py`, not a recurrence of the finish-token problem. The `proxy_root_asset` handler read only `request.match_info["tail"]`. For a route such as `/app/{tail:.*}`, aiohttp sets that tail to `ui.js`, so the proxy requested `http://127.0.0.1:6081/ui.js` instead of the required `/app/ui.js`. The direct noVNC server had the correct file; the proxy dropped its directory prefix and caused the 404.

### Fix applied

- `proxy_root_asset` now forwards `request.path.lstrip("/")`, preserving the complete path such as `app/ui.js`, `core/rfb.js`, `defaults.json`, and `websockify`.
- Added `scripts/telegram-lab/test_remote_console.py`. It starts a local fake noVNC upstream and verifies that root assets retain their full URL path through the protected proxy, including nested paths, and that the legacy `/novnc/vnc.html` route still maps correctly.
- Telegram Session CI Lab now runs this regression test immediately after installing its pinned aiohttp dependency and before emulator creation.
- Android CI also installs the same pinned aiohttp version and runs the regression test plus syntax compilation, so this routing bug is caught on every push without requiring another emulator bootstrap.

### Commits and verification status

- Proxy fix: `aeff39e293ee85dc49c6dc7feaf5ef64dd3f270e`.
- Regression test: `922bdfc4c071cefd9549628de37c1caa14dfd5aa`.
- Telegram workflow preflight test: `204bea86ab084231a886ca92e8b6ddab834edb73`.
- Android CI regression-test integration: `da15a9e3d171fe72a23b23aafbad64b14b6f6283`.
- Android CI run `#222` (Run ID `46074595683`) was queued when this note was written. Its completion and the next Telegram bootstrap are still required validation; do not claim the protected UI is confirmed visible yet.
- Run #10 never created a Cloudflare URL and did not open Telegram login or save an authenticated session.
- Run #10: https://github.com/CybersecurityRahbar/osint-android/actions/runs/46074015993



### Regression-test result — Android CI #224 (2026-11-28)
- Android CI #224, Run ID `46074640376`, checked out commit `308d12618b2c56b2aa885f550d5f3c9a4efc3a8f`.
- The `Validate Telegram CI helper scripts` step completed with `success`. This step installed the pinned `aiohttp==3.11.18`, compiled the proxy and test scripts, and executed `scripts/telegram-lab/test_remote_console.py`; therefore the root-resource path regression tests and legacy route test passed.
- The overall Android CI run was still in progress at the time of this note (Android runtime parser tests/build remained); do not describe the entire Android CI run as successful based only on the helper-test step.
- The Telegram Session CI Lab workflow has also been configured to run the same regression test immediately after dependency installation and before emulator creation. A new Telegram bootstrap is still required to validate the real Ubuntu noVNC package, reverse proxy, Cloudflare tunnel, and visible Android desktop end-to-end.
