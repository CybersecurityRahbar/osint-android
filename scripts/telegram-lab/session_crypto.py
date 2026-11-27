#!/usr/bin/env python3
"""Authenticated encryption for the dedicated Telegram CI emulator disk image."""
from __future__ import annotations

import argparse
import io
import os
import re
import tarfile
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"TGCI1"
ALLOWED_IMAGES = ("userdata-qemu.img", "encryptionkey.img", "sdcard.img")
AAD = b"osint-android/telegram-ci-session/v1"


def load_key() -> bytes:
    value = os.environ.get("TELEGRAM_CI_SESSION_KEY", "").strip()
    if not re.fullmatch(r"[0-9a-fA-F]{64}", value):
        raise SystemExit(
            "TELEGRAM_CI_SESSION_KEY must be exactly 64 hexadecimal characters (32 bytes)."
        )
    return bytes.fromhex(value)


def encrypt(avd_dir: Path, output: Path) -> None:
    buffer = io.BytesIO()
    found: list[str] = []
    with tarfile.open(fileobj=buffer, mode="w:gz") as archive:
        for name in ALLOWED_IMAGES:
            image = avd_dir / name
            if image.is_file():
                archive.add(image, arcname=name, recursive=False)
                found.append(name)

    if "userdata-qemu.img" not in found:
        raise SystemExit(
            "The emulator did not create userdata-qemu.img; refusing to save an incomplete session."
        )

    nonce = os.urandom(12)
    ciphertext = AESGCM(load_key()).encrypt(nonce, buffer.getvalue(), AAD)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(MAGIC + nonce + ciphertext)
    os.chmod(output, 0o600)
    print("Encrypted Telegram CI emulator state saved. Image members: " + ", ".join(found))


def decrypt(source: Path, avd_dir: Path) -> None:
    blob = source.read_bytes()
    if len(blob) < len(MAGIC) + 12 + 16 or not blob.startswith(MAGIC):
        raise SystemExit("Cached Telegram CI state has an invalid format.")

    nonce = blob[len(MAGIC):len(MAGIC) + 12]
    ciphertext = blob[len(MAGIC) + 12:]
    try:
        plaintext = AESGCM(load_key()).decrypt(nonce, ciphertext, AAD)
    except Exception:
        raise SystemExit(
            "Unable to decrypt Telegram CI state. Check that TELEGRAM_CI_SESSION_KEY is unchanged."
        )

    avd_dir.mkdir(parents=True, exist_ok=True)
    restored: list[str] = []
    with tarfile.open(fileobj=io.BytesIO(plaintext), mode="r:gz") as archive:
        for member in archive.getmembers():
            if member.name not in ALLOWED_IMAGES or not member.isfile():
                raise SystemExit("Refusing an unexpected path in the encrypted emulator image.")
            source_file = archive.extractfile(member)
            if source_file is None:
                raise SystemExit("Unable to read an emulator image from the encrypted archive.")
            destination = avd_dir / member.name
            with destination.open("wb") as output:
                while True:
                    chunk = source_file.read(1024 * 1024)
                    if not chunk:
                        break
                    output.write(chunk)
            restored.append(member.name)

    if "userdata-qemu.img" not in restored:
        raise SystemExit("Encrypted Telegram CI state is missing userdata-qemu.img.")
    print("Encrypted Telegram CI emulator state restored. Image members: " + ", ".join(restored))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("encrypt", "decrypt"))
    parser.add_argument("--avd-dir", required=True)
    parser.add_argument("--file", required=True)
    args = parser.parse_args()

    if args.mode == "encrypt":
        encrypt(Path(args.avd_dir), Path(args.file))
    else:
        decrypt(Path(args.file), Path(args.avd_dir))


if __name__ == "__main__":
    main()
