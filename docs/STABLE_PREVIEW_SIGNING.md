# Stable preview signing — osint-android

The v0.7.0 preview must use the same private signing key as the earlier `osint-web` preview channel. This preserves Android's ability to update the same installed package in place.

## Verified signing identity

The existing signing material was checked from the previously saved owner-held files. The PKCS#12 keystore opens successfully and contains a private-key entry with:

- Alias: `osint-preview`
- Store type: `PKCS12`
- Certificate SHA-256: `A2:0C:31:09:17:45:EA:92:F8:BF:A3:57:07:FA:13:A6:5D:5F:AB:6D:7C:4A:C8:3D:A7:F6:8C:DE:A5:3B:4E:78`

This fingerprint is a public certificate identifier, not the private key. Keep the keystore and password private. Never put either value, the Base64 keystore text, or the PKCS#12 file in this public repository.

## Configure this repository's Actions secrets

Actions secrets created at repository scope in `osint-web` do not automatically appear in `osint-android`. Add the same two values to this repository:

1. Open **Settings → Secrets and variables → Actions → Secrets → New repository secret**.
2. Create `PREVIEW_KEYSTORE_BASE64` using the complete Base64 text from the saved `OSINT_PREVIEW_KEYSTORE_BASE64.txt` file.
3. Create `PREVIEW_KEYSTORE_PASSWORD` using the exact contents of the saved `OSINT_PREVIEW_KEYSTORE_PASSWORD.txt` file.
4. Do not change the alias; the workflow and Gradle configuration use `osint-preview`.

Repository settings: https://github.com/CybersecurityRahbar/osint-android/settings/secrets/actions

## Build and download

After both secrets are configured, open **Actions → Android CI → Run workflow**, select `main`, and run it. Download the artifact named:

`osint-workbench-v0.7.0-preview-apk`

The downloaded artifact is a ZIP; extract and install `app-debug.apk`, not the ZIP.

The current preview package ID is:

`com.cyberphantom.osintworkbench.preview051`

Keep this application ID, the `osint-preview` alias, the same keystore, and the same passwords for every future update. Increase `versionCode` for each build. The workflow deliberately does not upload an installable APK when signing secrets are absent or invalid; a successful compile-only build is not proof that the APK can update the installed app.

## Important installation rule

Android checks the signing certificate when updating an existing app. A new APK with a different certificate cannot update the same package as a normal in-place update. Keep the private signing key backed up securely and never regenerate it for this application channel.

The older `.preview050` package was a separate transition build. The stable update channel is `.preview051`; a build from this repository will update an already installed `.preview051` app only when that installed app was signed with this same certificate.
