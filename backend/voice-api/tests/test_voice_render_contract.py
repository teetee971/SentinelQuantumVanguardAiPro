from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
RENDER_YAML = ROOT / "render.yaml"


def test_render_declares_separate_voice_service_closed_by_default():
    text = RENDER_YAML.read_text(encoding="utf-8")

    assert "name: sentinel-voice-api" in text
    assert "dockerfilePath: ./backend/voice-api/Dockerfile" in text
    assert "healthCheckPath: /health/live" in text
    assert "- key: VOICE_SERVICE_ENABLED\n        value: \"false\"" in text


def test_render_voice_secrets_are_not_committed():
    text = RENDER_YAML.read_text(encoding="utf-8")

    for key in (
        "VOICE_REDIS_URL",
        "LIVEKIT_API_KEY",
        "LIVEKIT_API_SECRET",
        "VOICE_AUTH_JWKS_URL",
        "VOICE_AUTH_ISSUER",
        "VOICE_AUTH_AUDIENCE",
    ):
        assert f"- key: {key}\n        sync: false" in text
