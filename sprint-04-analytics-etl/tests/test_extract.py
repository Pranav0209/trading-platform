from __future__ import annotations

import json

import pytest

from sprint_04_analytics_etl.extract import extract_candles, get_usage


class FakeResponse:
    def __init__(self, payload: dict):
        self.status_code = 200
        self.headers = {}
        self._payload = payload

    def json(self) -> dict:
        return self._payload

    def raise_for_status(self) -> None:
        return None


class FakeSession:
    def __init__(self, payloads: list[dict]):
        self.payloads = payloads
        self.calls: list[dict] = []

    def get(self, url: str, headers=None, params=None, timeout=None):
        self.calls.append({"url": url, "headers": headers, "params": params, "timeout": timeout})
        payload = self.payloads[min(len(self.calls) - 1, len(self.payloads) - 1)]
        return FakeResponse(payload)


def _payload(symbol: str = "INFY.NS") -> dict:
    return {
        "data": {
            "symbol": symbol,
            "interval": "1d",
            "currency": "INR",
            "candles": [
                {
                    "date": "2026-07-01",
                    "open": 1,
                    "high": 2,
                    "low": 1,
                    "close": 2,
                    "adjclose": 2,
                    "volume": 1,
                    "synthetic": False,
                }
            ],
        },
        "meta": {"symbol": symbol, "asOf": "2026-07-02T00:00:00Z"},
    }


def test_extract_candles_uses_env_key_and_caches_raw_response_by_range(tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("FAUXNANCE_API_KEY", "env-key")
    monkeypatch.setenv("FAUXNANCE_BASE_URL", "https://example.test/v1")
    session = FakeSession([_payload()])

    first = extract_candles(
        "INFY.NS",
        from_date="2026-07-01",
        to_date="2026-07-31",
        cache_dir=tmp_path,
        session=session,
    )

    cached_file = tmp_path / "INFY.NS__from_2026-07-01__to_2026-07-31.json"
    assert cached_file.exists()
    assert json.loads(cached_file.read_text(encoding="utf-8")) == first
    assert session.calls[0]["headers"] == {"X-Api-Key": "env-key"}
    assert session.calls[0]["params"] == {"from": "2026-07-01", "to": "2026-07-31"}

    second = extract_candles(
        "INFY.NS",
        from_date="2026-07-01",
        to_date="2026-07-31",
        cache_dir=tmp_path,
        session=FakeSession([_payload("SHOULD-NOT-BE-USED")]),
    )

    assert second == first
    assert len(session.calls) == 1


def test_get_usage_requires_the_environment_key(monkeypatch) -> None:
    monkeypatch.delenv("FAUXNANCE_API_KEY", raising=False)
    monkeypatch.setenv("FAUXNANCE_BASE_URL", "https://example.test/v1")

    with pytest.raises(RuntimeError, match="FAUXNANCE_API_KEY"):
        get_usage()