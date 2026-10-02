from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RENDER_YAML = ROOT / "render.yaml"


def test_render_blueprint_declares_required_server_only_secrets():
    text = RENDER_YAML.read_text(encoding="utf-8")

    for key in (
        "PHONE_HASH_PEPPER",
        "PUBLIC_REPORT_PEPPER",
        "INDICATOR_HASH_PEPPER",
        "EXPOSURE_HASH_PEPPER",
        "EXPOSURE_API_KEY",
        "REPORT_API_KEY",
        "MODERATION_API_KEY",
        "RATE_LIMIT_PEPPER",
    ):
        marker = f"- key: {key}\n        generateValue: true"
        assert marker in text, f"{key} must be generated server-side by Render"


def test_render_blueprint_declares_explicit_reporting_limits():
    text = RENDER_YAML.read_text(encoding="utf-8")

    expected = {
        "PUBLIC_REPORT_RATE_LIMIT_PER_MINUTE": "3",
        "MODERATION_RATE_LIMIT_PER_MINUTE": "30",
        "INTEL_PUBLIC_REPORT_RATE_LIMIT_PER_MINUTE": "5",
        "INTEL_RELATIONSHIP_RATE_LIMIT_PER_MINUTE": "30",
        "INTEL_GRAPH_LOOKUP_RATE_LIMIT_PER_MINUTE": "30",
        "INTEL_EXPOSURE_REPORT_RATE_LIMIT_PER_MINUTE": "30",
        "INTEL_EXPOSURE_LOOKUP_RATE_LIMIT_PER_MINUTE": "30",
        "INTEL_MODERATION_RATE_LIMIT_PER_MINUTE": "30",
    }
    for key, value in expected.items():
        marker = f'- key: {key}\n        value: "{value}"'
        assert marker in text, f"{key} must be explicit in Render blueprint"
