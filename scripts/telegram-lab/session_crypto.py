#!/usr/bin/env python3
"""Streaming AES-256-GCM encryption for the dedicated Telegram CI emulator disk.

No additional key is requested from the user. The AES key is derived inside CI from
the existing stable APK signing-keystore secret using HKDF and a per-archive salt.
"""
from __future__ import annotations

import argparse
import base64
import os
import tarfile
import tempfile
from pathlib import Path

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

MAGIC = b"TGCI2"
SALT_SIZE = 16
NONCE_SIZE = 12
HEADER_SIZE = len(MAGIC) + SALT_SIZE + NONCE_SIZE
TAG_SIZE = 16
CHUNK_SIZE = 1024 * 1024
ALLOWED_IMAGES = ("userdata-qemu.img", "encryptionkey.img", "sdcard.img")
AAD = b"osint-android/telegram-ci-session/v2"
HKDF_INFO = b"osint-android/telegram-ci-session-key/v2"


def derive_key(salt: bytes) -> bytes:
    encoded_keystore = os.environ.get("PREVIEW_KEYSTORE_BASE64", "").strip()
    keystore_password = os.environ.get("PREVIEW_KEYSTORE_PASSWORD", "")
    if not encoded_keystore or not keystore_password:
        raise SystemExit(
            "Stable preview signing secrets are required to derive the Telegram CI encryption key."
        )
    try:
        keystore_bytes = base64.b64decode(encoded_keystore, validate=True)
    except Exception:
        raise SystemExit("PREVIEW_KEYSTORE_BASE64 is not valid base64.")
    if len(keystore_bytes) < 32:
        raise SystemExit("The stable preview keystore data is unexpectedly short.")

    # Domain-separated key derivation. The existing signing secret is never printed,
    # written to disk by this helper, or copied into the repository.
    input_key_material = keystore_bytes + b"\x00" + keystore_password.encode("utf-8")
    return HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        info=HKDF_INFO,
    ).derive(input_key_material)


def create_archive(avd_dir: Path, archive_path: Path) -> list[str]:
    found: list[str] = []
    with tarfile.open(archive_path, mode="w:gz") as archive:
        for name in ALLOWED_IMAGES:
            image = avd_dir / name
            if image.is_file():
                archive.add(image, arcname=name, recursive=False)
                found.append(name)
    if "userdata-qemu.img" not in found:
        raise SystemExit(
            "The emulator did not create userdata-qemu.img; refusing to save an incomplete session."
        )
    return found


def encrypt(avd_dir: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    os.chmod(output.parent, 0o700)

    with tempfile.TemporaryDirectory(prefix="telegram-ci-") as temporary:
        archive_path = Path(temporary) / "emulator-state.tar.gz"
        found = create_archive(avd_dir, archive_path)

        salt = os.urandom(SALT_SIZE)
        nonce = os.urandom(NONCE_SIZE)
        encryptor = Cipher(
            algorithms.AES(derive_key(salt)),
            modes.GCM(nonce),
        ).encryptor()
        encryptor.authenticate_additional_data(AAD)

        temporary_output = output.with_suffix(output.suffix + ".tmp")
        with archive_path.open("rb") as source, temporary_output.open("wb") as target:
            target.write(MAGIC + salt + nonce)
            while True:
                chunk = source.read(CHUNK_SIZE)
                if not chunk:
                    break
                encrypted = encryptor.update(chunk)
                if encrypted:
                    target.write(encrypted)
            tail = encryptor.finalize()
            if tail:
                target.write(tail)
            target.write(encryptor.tag)

        os.chmod(temporary_output, 0o600)
        temporary_output.replace(output)
    print("Encrypted Telegram CI emulator state saved. Image members: " + ", ".join(found))


def decrypt(source: Path, avd_dir: Path) -> None:
    file_size = source.stat().st_size
    if file_size < HEADER_SIZE + TAG_SIZE:
        raise SystemExit("Cached Telegram CI state has an invalid format.")

    avd_dir.mkdir(parents=True, exist_ok=True)
    with source.open("rb") as encrypted_file:
        prefix = encrypted_file.read(HEADER_SIZE)
        if len(prefix) != HEADER_SIZE or not prefix.startswith(MAGIC):
            raise SystemExit("Cached Telegram CI state has an invalid format.")

        salt_start = len(MAGIC)
        salt = prefix[salt_start:salt_start + SALT_SIZE]
        nonce = prefix[salt_start + SALT_SIZE:]
        payload_size = file_size - HEADER_SIZE - TAG_SIZE
        encrypted_file.seek(file_size - TAG_SIZE)
        tag = encrypted_file.read(TAG_SIZE)
        encrypted_file.seek(HEADER_SIZE)

        decryptor = Cipher(
            algorithms.AES(derive_key(salt)),
            modes.GCM(nonce, tag),
        ).decryptor()
        decryptor.authenticate_additional_data(AAD)

        with tempfile.TemporaryDirectory(prefix="telegram-ci-restore-") as temporary:
            archive_path = Path(temporary) / "emulator-state.tar.gz"
            remaining = payload_size
            try:
                with archive_path.open("wb") as archive_file:
                    while remaining:
                        chunk = encrypted_file.read(min(CHUNK_SIZE, remaining))
                        if not chunk:
                            raise SystemExit("Encrypted Telegram CI state ended unexpectedly.")
                        remaining -= len(chunk)
                        plaintext = decryptor.update(chunk)
                        if plaintext:
                            archive_file.write(plaintext)
                    final = decryptor.finalize()
                    if final:
                        archive_file.write(final)
            except Exception:
                raise SystemExit(
                    "Unable to authenticate/decrypt Telegram CI state. Check the unchanged stable signing secrets."
                )

            restored: list[str] = []
            with tarfile.open(archive_path, mode="r:gz") as archive:
                for member in archive.getmembers():
                    if member.name not in ALLOWED_IMAGES or not member.isfile():
                        raise SystemExit("Refusing an unexpected path in the encrypted emulator image.")
                    source_file = archive.extractfile(member)
                    if source_file is None:
                        raise SystemExit("Unable to read an emulator image from the encrypted archive.")
                    destination = avd_dir / member.name
                    with destination.open("wb") as output_file:
                        while True:
                            chunk = source_file.read(CHUNK_SIZE)
                            if not chunk:
                                break
                            output_file.write(chunk)
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
