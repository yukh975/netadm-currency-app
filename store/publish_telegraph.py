#!/usr/bin/env python3
"""Публикация Markdown-страницы на telegra.ph и вывод публичного URL.

Открытый API Telegraph (без регистрации). Используется для хостинга политики
конфиденциальности (магазинам нужен публичный URL).

Пример:
    python3 store/publish_telegraph.py store/privacy-policy-ru.md "Политика конфиденциальности"
"""
from __future__ import annotations

import json
import sys
import urllib.parse
import urllib.request

API = "https://api.telegra.ph"


def _clean(text: str) -> str:
    return text.replace("**", "").replace("`", "").replace("•", "").strip()


def md_to_nodes(text: str) -> list:
    nodes: list = []
    list_buf: list = []

    def flush() -> None:
        nonlocal list_buf
        if list_buf:
            nodes.append({"tag": "ul", "children": [{"tag": "li", "children": [x]} for x in list_buf]})
            list_buf = []

    for raw in text.splitlines():
        s = raw.rstrip()
        if not s.strip():
            flush()
            continue
        if s.startswith("# "):          # верхний заголовок = title страницы, пропускаем
            continue
        if s.startswith("### "):
            flush(); nodes.append({"tag": "h4", "children": [_clean(s[4:])]})
        elif s.startswith("## "):
            flush(); nodes.append({"tag": "h3", "children": [_clean(s[3:])]})
        elif s.lstrip().startswith("- "):
            list_buf.append(_clean(s.lstrip()[2:]))
        elif s.startswith("_") and s.endswith("_"):
            flush(); nodes.append({"tag": "p", "children": [{"tag": "em", "children": [_clean(s[1:-1])]}]})
        else:
            flush(); nodes.append({"tag": "p", "children": [_clean(s)]})
    flush()
    return nodes


def _post(method: str, params: dict) -> dict:
    data = urllib.parse.urlencode(params).encode("utf-8")
    with urllib.request.urlopen(f"{API}/{method}", data=data, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> None:
    path, title = sys.argv[1], sys.argv[2]
    with open(path, encoding="utf-8") as f:
        content = md_to_nodes(f.read())

    acc = _post("createAccount", {"short_name": "CurrencyConverter", "author_name": "Конвертер валют"})
    if not acc.get("ok"):
        raise SystemExit(f"createAccount failed: {acc}")
    token = acc["result"]["access_token"]

    page = _post("createPage", {
        "access_token": token,
        "title": title,
        "author_name": "Конвертер валют",
        "content": json.dumps(content, ensure_ascii=False),
    })
    if not page.get("ok"):
        raise SystemExit(f"createPage failed: {page}")
    print(page["result"]["url"])


if __name__ == "__main__":
    main()
