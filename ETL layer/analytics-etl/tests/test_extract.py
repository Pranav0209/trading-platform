from __future__ import annotations

import json

import pytest
import requests

from analytics_etl.extract import (
    FauxnanceHTTPError,
    FauxnanceNetworkError,
    FauxnanceRateLimitError,
    extract_candles,
    get_usage,
)


class FakeResponse:
    def __init__(
        self,
        payload: dict | None = None,
        *,
        status_code: int = 200,
        headers: dict[str, str] | None = None,
    ):
        self.status_code = status_code
        self.headers = headers or {}
        self._payload = payload or {}

    def json(self) -> dict:
        return self._payload

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise requests.HTTPError(
                f"HTTP {self.status_code}"
            )


class FakeSession:
    def __init__(self, responses: list[FakeResponse | Exception]):
        self.responses = responses
        self.calls: list[dict] = []

    def get(
        self,
        url: str,
        headers=None,
        params=None,
        timeout=None,
    ):
        self.calls.append(
            {
                "url": url,
                "headers": headers,
                "params": params,
                "timeout": timeout,
            }
        )

        response = self.responses[
            min(
                len(self.calls) - 1,
                len(self.responses) - 1,
            )
        ]

        if isinstance(response, Exception):
            raise response

        return response


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
        "meta": {
            "symbol": symbol,
            "asOf": "2026-07-02T00:00:00Z",
        },
    }


def test_extract_candles_uses_env_key_and_caches_raw_response_by_range(
    tmp_path,
    monkeypatch,
) -> None:
    monkeypatch.setenv("FAUXNANCE_API_KEY", "env-key")
    monkeypatch.setenv(
        "FAUXNANCE_BASE_URL",
        "https://example.test/v1",
    )

    session = FakeSession(
        [FakeResponse(_payload())]
    )

    first = extract_candles(
        "INFY.NS",
        from_date="2026-07-01",
        to_date="2026-07-31",
        cache_dir=tmp_path,
        session=session,
    )

    cached_file = (
        tmp_path
        / "INFY.NS__from_2026-07-01__to_2026-07-31.json"
    )

    assert cached_file.exists()

    assert (
        json.loads(
            cached_file.read_text(encoding="utf-8")
        )
        == first
    )

    assert session.calls[0]["headers"] == {
        "X-Api-Key": "env-key"
    }

    assert session.calls[0]["params"] == {
        "from": "2026-07-01",
        "to": "2026-07-31",
    }

    second_session = FakeSession(
        [
            FakeResponse(
                _payload("SHOULD-NOT-BE-USED")
            )
        ]
    )

    second = extract_candles(
        "INFY.NS",
        from_date="2026-07-01",
        to_date="2026-07-31",
        cache_dir=tmp_path,
        session=second_session,
    )

    assert second == first
    assert len(second_session.calls) == 0


def test_get_usage_requires_the_environment_key(
    monkeypatch,
) -> None:
    monkeypatch.delenv(
        "FAUXNANCE_API_KEY",
        raising=False,
    )
    monkeypatch.setenv(
        "FAUXNANCE_BASE_URL",
        "https://example.test/v1",
    )

    with pytest.raises(
        RuntimeError,
        match="FAUXNANCE_API_KEY",
    ):
        get_usage()


def test_network_error_retries_with_increasing_backoff(
    monkeypatch,
    tmp_path,
) -> None:
    monkeypatch.setenv(
        "FAUXNANCE_API_KEY",
        "test-key",
    )

    session = FakeSession(
        [
            requests.ConnectionError("connection failed"),
            requests.Timeout("timeout"),
            FakeResponse(_payload()),
        ]
    )

    sleep_calls: list[float] = []

    monkeypatch.setattr(
        "analytics_etl.extract.time.sleep",
        sleep_calls.append,
    )

    result = extract_candles(
        "INFY.NS",
        base_url="https://example.test/v1",
        cache_dir=tmp_path,
        session=session,
    )

    assert result == _payload()
    assert len(session.calls) == 3
    assert sleep_calls == [1, 2]


def test_network_error_fails_after_three_attempts(
    monkeypatch,
    tmp_path,
) -> None:
    session = FakeSession(
        [
            requests.ConnectionError("connection failed"),
            requests.ConnectionError("connection failed"),
            requests.ConnectionError("connection failed"),
        ]
    )

    sleep_calls: list[float] = []

    monkeypatch.setattr(
        "analytics_etl.extract.time.sleep",
        sleep_calls.append,
    )

    with pytest.raises(FauxnanceNetworkError):
        extract_candles(
            "INFY.NS",
            api_key="test-key",
            base_url="https://example.test/v1",
            cache_dir=tmp_path,
            session=session,
        )

    assert len(session.calls) == 3
    assert sleep_calls == [1, 2]


def test_5xx_retries_with_increasing_backoff(
    monkeypatch,
    tmp_path,
) -> None:
    session = FakeSession(
        [
            FakeResponse(status_code=503),
            FakeResponse(status_code=500),
            FakeResponse(_payload()),
        ]
    )

    sleep_calls: list[float] = []

    monkeypatch.setattr(
        "analytics_etl.extract.time.sleep",
        sleep_calls.append,
    )

    result = extract_candles(
        "INFY.NS",
        api_key="test-key",
        base_url="https://example.test/v1",
        cache_dir=tmp_path,
        session=session,
    )

    assert result == _payload()
    assert len(session.calls) == 3
    assert sleep_calls == [1, 2]


def test_5xx_fails_after_three_attempts(
    monkeypatch,
    tmp_path,
) -> None:
    session = FakeSession(
        [
            FakeResponse(status_code=500),
            FakeResponse(status_code=502),
            FakeResponse(status_code=503),
        ]
    )

    sleep_calls: list[float] = []

    monkeypatch.setattr(
        "analytics_etl.extract.time.sleep",
        sleep_calls.append,
    )

    with pytest.raises(
        FauxnanceHTTPError,
        match="HTTP 503",
    ):
        extract_candles(
            "INFY.NS",
            api_key="test-key",
            base_url="https://example.test/v1",
            cache_dir=tmp_path,
            session=session,
        )

    assert len(session.calls) == 3
    assert sleep_calls == [1, 2]


@pytest.mark.parametrize(
    "status_code",
    [400, 401, 403, 404],
)
def test_4xx_fails_immediately_without_retry(
    status_code,
    monkeypatch,
    tmp_path,
) -> None:
    session = FakeSession(
        [
            FakeResponse(status_code=status_code),
        ]
    )

    sleep_calls: list[float] = []

    monkeypatch.setattr(
        "analytics_etl.extract.time.sleep",
        sleep_calls.append,
    )

    with pytest.raises(
        FauxnanceHTTPError,
        match=f"HTTP {status_code}",
    ):
        extract_candles(
            "INFY.NS",
            api_key="test-key",
            base_url="https://example.test/v1",
            cache_dir=tmp_path,
            session=session,
        )

    assert len(session.calls) == 1
    assert sleep_calls == []


def test_429_raises_rate_limit_error_with_retry_after(
    monkeypatch,
    tmp_path,
) -> None:
    session = FakeSession(
        [
            FakeResponse(
                status_code=429,
                headers={"Retry-After": "120"},
            )
        ]
    )

    sleep_calls: list[float] = []

    monkeypatch.setattr(
        "analytics_etl.extract.time.sleep",
        sleep_calls.append,
    )

    with pytest.raises(
        FauxnanceRateLimitError,
        match="retry after 120 seconds",
    ):
        extract_candles(
            "INFY.NS",
            api_key="test-key",
            base_url="https://example.test/v1",
            cache_dir=tmp_path,
            session=session,
        )

    assert len(session.calls) == 1
    assert sleep_calls == []