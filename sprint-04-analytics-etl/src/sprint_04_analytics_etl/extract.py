from __future__ import annotations

import json
import os
import time
from pathlib import Path
from typing import Any

import requests

def _cache_path(cache_dir: Path, symbol: str, from_date: str | None, to_date: str | None) -> Path:
    range_token = "__".join(
        [
            f"from_{from_date or 'all'}",
            f"to_{to_date or 'all'}",
        ]
    )
    return cache_dir / f"{symbol.replace('/', '_')}__{range_token}.json"


def _resolve_base_url(base_url: str | None) -> str:
    resolved_base_url = base_url or os.environ.get("FAUXNANCE_BASE_URL")
    if not resolved_base_url:
        raise RuntimeError("FAUXNANCE_BASE_URL is required to fetch candles")
    return resolved_base_url.rstrip("/")


class FauxnanceRateLimitError(RuntimeError):
    """Raised when Fauxnance returns HTTP 429."""


class FauxnanceHTTPError(RuntimeError):
    """Raised when Fauxnance returns an HTTP error."""


class FauxnanceNetworkError(RuntimeError):
    """Raised when the request fails after network retries."""


def _request_json(
    session: requests.Session,
    url: str,
    *,
    api_key: str | None = None,
    params: dict[str, str] | None = None,
) -> dict[str, Any]:
    headers = {"X-Api-Key": api_key} if api_key else None

    max_attempts = 3
    base_backoff_seconds = 1

    for attempt in range(1, max_attempts + 1):
        try:
            response = session.get(
                url,
                headers=headers,
                params=params,
                timeout=30,
            )

        except requests.RequestException as exc:
            if attempt == max_attempts:
                raise FauxnanceNetworkError(
                    f"Fauxnance request failed after {max_attempts} attempts"
                ) from exc

            backoff_seconds = base_backoff_seconds * (2 ** (attempt - 1))
            time.sleep(backoff_seconds)
            continue

        # 429 is special: quota/rate-limit problem.
        # Do not automatically sleep for an arbitrary amount of time.
        if response.status_code == 429:
            retry_after = response.headers.get("Retry-After")

            message = "Fauxnance rate limit reached"
            if retry_after:
                message = (
                    f"{message}; retry after {retry_after} seconds"
                )

            raise FauxnanceRateLimitError(message)

        # Other 4xx errors are client/request problems.
        # Retrying the exact same request won't normally fix them.
        if 400 <= response.status_code < 500:
            raise FauxnanceHTTPError(
                f"Fauxnance request failed with HTTP "
                f"{response.status_code}"
            )

        # 5xx means the server may be temporarily unavailable.
        # Retry with exponential backoff.
        if 500 <= response.status_code < 600:
            if attempt == max_attempts:
                raise FauxnanceHTTPError(
                    f"Fauxnance server failed with HTTP "
                    f"{response.status_code} after "
                    f"{max_attempts} attempts"
                )

            backoff_seconds = base_backoff_seconds * (2 ** (attempt - 1))
            time.sleep(backoff_seconds)
            continue

        # 2xx response.
        try:
            return response.json()
        except ValueError as exc:
            raise FauxnanceHTTPError(
                "Fauxnance returned a successful HTTP response "
                "but the response was not valid JSON"
            ) from exc

    raise RuntimeError("Fauxnance request failed unexpectedly")


def _load_or_fetch_candles(
    symbol: str,
    *,
    from_date: str | None,
    to_date: str | None,
    api_key: str | None,
    base_url: str | None,
    cache_dir: str | Path,
    cache_ttl: int | None = 86400,
    session: requests.Session | None,
) -> dict[str, Any]:
    cache_path = _cache_path(Path(cache_dir), symbol, from_date, to_date)
    cache_path.parent.mkdir(parents=True, exist_ok=True)

    if cache_path.exists():
        if cache_ttl is None or (time.time() - cache_path.stat().st_mtime) < cache_ttl:
            return json.loads(cache_path.read_text(encoding="utf-8"))

    resolved_api_key = api_key or os.environ.get("FAUXNANCE_API_KEY")
    if not resolved_api_key:
        raise RuntimeError("FAUXNANCE_API_KEY is required to fetch candles")

    resolved_base_url = _resolve_base_url(base_url)
    params = {key: value for key, value in (("from", from_date), ("to", to_date)) if value}
    client = session or requests.Session()
    payload = _request_json(client, f"{resolved_base_url}/candles/{symbol}", api_key=resolved_api_key, params=params or None)
    cache_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    return payload


def extract_candles(
    symbol: str,
    *,
    from_date: str | None = None,
    to_date: str | None = None,
    api_key: str | None = None,
    base_url: str | None = None,
    cache_dir: str | Path = ".cache",
    cache_ttl: int | None = 86400,
    session: requests.Session | None = None,
) -> dict[str, Any]:
    return _load_or_fetch_candles(
        symbol,
        from_date=from_date,
        to_date=to_date,
        api_key=api_key,
        base_url=base_url,
        cache_dir=cache_dir,
        cache_ttl=cache_ttl,
        session=session,
    )


def check_health(*, base_url: str | None = None, session: requests.Session | None = None) -> dict[str, Any]:
    resolved_base_url = _resolve_base_url(base_url)
    client = session or requests.Session()
    response = client.get(f"{resolved_base_url}/health", timeout=30)
    response.raise_for_status()
    return response.json()


def get_usage(*, api_key: str | None = None, base_url: str | None = None, session: requests.Session | None = None) -> dict[str, Any]:
    resolved_api_key = api_key or os.environ.get("FAUXNANCE_API_KEY")
    if not resolved_api_key:
        raise RuntimeError("FAUXNANCE_API_KEY is required to fetch usage")

    resolved_base_url = _resolve_base_url(base_url)
    client = session or requests.Session()
    return _request_json(client, f"{resolved_base_url}/usage", api_key=resolved_api_key)