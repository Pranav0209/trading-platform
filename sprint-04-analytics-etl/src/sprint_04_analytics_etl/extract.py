from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

import requests


def _cache_path(cache_dir: Path, symbol: str) -> Path:
    return cache_dir / f"{symbol.replace('/', '_')}.json"


def _request_json(session: requests.Session, url: str, api_key: str) -> dict[str, Any]:
    headers = {"X-Api-Key": api_key}

    for _ in range(3):
        try:
            response = session.get(url, headers=headers, timeout=30)
        except requests.RequestException:
            continue

        if response.status_code == 429:
            retry_after = response.headers.get("Retry-After")
            message = "Fauxnance rate limit reached"
            if retry_after:
                message = f"{message}; retry after {retry_after} seconds"
            raise RuntimeError(message)

        if response.status_code >= 400:
            raise RuntimeError(f"Fauxnance request failed with HTTP {response.status_code}")

        return response.json()

    raise RuntimeError("Fauxnance request failed after retries")


def extract_candles(
    symbol: str,
    *,
    api_key: str | None = None,
    base_url: str | None = None,
    cache_dir: str | Path = ".cache",
    session: requests.Session | None = None,
) -> dict[str, Any]:
    cache_path = _cache_path(Path(cache_dir), symbol)
    cache_path.parent.mkdir(parents=True, exist_ok=True)

    if cache_path.exists():
        return json.loads(cache_path.read_text(encoding="utf-8"))

    resolved_api_key = api_key or os.environ.get("FAUXNANCE_API_KEY")
    if not resolved_api_key:
        raise RuntimeError("FAUXNANCE_API_KEY is required to fetch candles")

    resolved_base_url = base_url or os.environ.get("FAUXNANCE_BASE_URL")
    if not resolved_base_url:
        raise RuntimeError("FAUXNANCE_BASE_URL is required to fetch candles")
    resolved_base_url = resolved_base_url.rstrip("/")
    client = session or requests.Session()
    payload = _request_json(client, f"{resolved_base_url}/candles/{symbol}", resolved_api_key)
    cache_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    return payload