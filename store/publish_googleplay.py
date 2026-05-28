#!/usr/bin/env python3
"""Загрузка AAB в Google Play через Android Publisher API.

Требуется service account с доступом в Play Console и правом публикации.
Зависимости: google-api-python-client, google-auth.

Пример:
    python3 store/publish_googleplay.py \
        --aab app/build/outputs/bundle/release/app-release.aab \
        --package net.yukh.currency --track internal --credentials sa.json
"""
from __future__ import annotations

import argparse

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--aab", required=True)
    ap.add_argument("--package", required=True)
    ap.add_argument("--track", default="internal",
                    help="internal | alpha | beta | production")
    ap.add_argument("--credentials", required=True, help="service account JSON")
    args = ap.parse_args()

    creds = service_account.Credentials.from_service_account_file(
        args.credentials, scopes=[SCOPE],
    )
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

    edit = service.edits().insert(packageName=args.package, body={}).execute()
    edit_id = edit["id"]

    media = MediaFileUpload(args.aab, mimetype="application/octet-stream", resumable=True)
    bundle = service.edits().bundles().upload(
        packageName=args.package, editId=edit_id, media_body=media,
    ).execute()
    version_code = bundle["versionCode"]
    print(f"Uploaded bundle versionCode={version_code}")

    service.edits().tracks().update(
        packageName=args.package,
        editId=edit_id,
        track=args.track,
        body={"releases": [{"versionCodes": [version_code], "status": "completed"}]},
    ).execute()

    service.edits().commit(packageName=args.package, editId=edit_id).execute()
    print(f"Committed: versionCode {version_code} -> track '{args.track}'")


if __name__ == "__main__":
    main()
