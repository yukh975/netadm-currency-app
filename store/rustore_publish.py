#!/usr/bin/env python3
"""Загрузка APK в RuStore через публичное API.

Документация: https://www.rustore.ru/help/work-with-rustore-api/
Нужны: keyId (идентификатор ключа из консоли RuStore) и приватный RSA-ключ
(PKCS#8 PEM). Зависимости: requests, cryptography.

⚠️ Скрипт следует документированному потоку, но API RuStore может меняться —
при первом запуске сверьте ответы и при необходимости поправьте эндпоинты.

Пример:
    python3 store/rustore_publish.py \
        --apk app/build/outputs/apk/release/app-release.apk \
        --package net.yukh.currency --key-id "$RUSTORE_KEY_ID" --key key.pem
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

API = "https://public-api.rustore.ru"


def make_token(key_id: str, key_path: str) -> str:
    with open(key_path, "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None)
    timestamp = datetime.now(timezone.utc).astimezone().isoformat(timespec="milliseconds")
    message = (key_id + timestamp).encode("utf-8")
    signature = private_key.sign(message, padding.PKCS1v15(), hashes.SHA512())
    body = {
        "keyId": key_id,
        "timestamp": timestamp,
        "signature": base64.b64encode(signature).decode("ascii"),
    }
    resp = requests.post(f"{API}/public/auth/", json=body, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    return data["body"]["jwe"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--package", required=True)
    ap.add_argument("--key-id", required=True)
    ap.add_argument("--key", required=True, help="RSA private key PEM (PKCS#8)")
    ap.add_argument("--whats-new", default="Обновление приложения.")
    ap.add_argument("--email", required=True, help="контактный e-mail разработчика (обязателен в черновике)")
    ap.add_argument("--min-android", type=int, default=7,
                    help="минимальная версия Android (число): minSdk 24 = Android 7")
    args = ap.parse_args()

    token = make_token(args.key_id, args.key)
    headers = {"Public-Token": token}

    # 1. Черновик новой версии.
    # ВНИМАНИЕ: minAndroidVersion и developerContacts стали ОБЯЗАТЕЛЬНЫМИ —
    # без них RuStore отклоняет создание версии. Схема developerContacts может
    # отличаться по версии API — сверьтесь с docs при первом запуске.
    resp = requests.post(
        f"{API}/public/v1/application/{args.package}/version",
        headers=headers,
        json={
            "whatsNew": args.whats_new,
            "publishType": "MANUAL",
            "minAndroidVersion": args.min_android,
            "developerContacts": [{"type": "EMAIL", "value": args.email}],
        },
        timeout=30,
    )
    resp.raise_for_status()
    version_id = resp.json()["body"]
    print(f"Draft versionId={version_id}")

    # 2. Загрузка APK. isMainApk=true стало ОБЯЗАТЕЛЬНЫМ параметром запроса.
    with open(args.apk, "rb") as f:
        resp = requests.post(
            f"{API}/public/v1/application/{args.package}/version/{version_id}/apk",
            headers=headers,
            params={"servicesType": "Unknown", "isMainApk": "true"},
            files={"file": (args.apk.split("/")[-1], f, "application/vnd.android.package-archive")},
            timeout=300,
        )
    resp.raise_for_status()
    print("APK uploaded")

    # 3. Отправка на публикацию (модерацию)
    resp = requests.post(
        f"{API}/public/v1/application/{args.package}/version/{version_id}/commit",
        headers=headers,
        timeout=60,
    )
    resp.raise_for_status()
    print(f"Submitted version {version_id} for review")


if __name__ == "__main__":
    main()
