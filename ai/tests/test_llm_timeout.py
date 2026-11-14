import pytest

from qa import llm_client


@pytest.mark.parametrize("value, expected", [
    (None, 20.0),      # GPU default unchanged
    ("40", 40.0),      # Oracle CPU profile
    ("1", 5.0),        # clamped low
    ("999", 120.0),    # clamped high
    ("abc", 20.0),     # invalid -> default
])
def test_request_timeout(monkeypatch, value, expected):
    if value is None:
        monkeypatch.delenv("LLM_TIMEOUT_SECONDS", raising=False)
    else:
        monkeypatch.setenv("LLM_TIMEOUT_SECONDS", value)
    assert llm_client.request_timeout() == expected


def test_post_uses_configured_timeout(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "ollama")
    monkeypatch.setenv("LLM_BASE_URL", "http://llm:11434")
    monkeypatch.setenv("LLM_MODEL", "qwen3:4b")
    monkeypatch.setenv("LLM_TIMEOUT_SECONDS", "40")
    seen = {}

    class Opener:
        def open(self, req, timeout):
            seen["timeout"] = timeout
            raise OSError("stop")

    monkeypatch.setattr(llm_client.urllib.request, "build_opener", lambda *a: Opener())
    with pytest.raises(RuntimeError, match="llm_unavailable"):
        llm_client._post({"model": "qwen3:4b"})
    assert seen["timeout"] == 40.0
