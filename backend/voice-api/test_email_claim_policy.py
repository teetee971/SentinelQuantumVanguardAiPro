"""Regression checks for verified-email claims in the voice API.

Run with: python -m unittest discover -s backend/voice-api -p 'test_email_claim_policy.py'
Requires the voice API's Python dependencies to be installed.
"""
import asyncio
import importlib.util
import pathlib
import unittest
from unittest.mock import patch

MODULE_PATH = pathlib.Path(__file__).with_name("app.py")
spec = importlib.util.spec_from_file_location("sentinel_voice_api_under_test", MODULE_PATH)
voice = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = voice
spec.loader.exec_module(voice)


class VerifiedEmailClaimTests(unittest.TestCase):
    def test_claim_namespace_defaults_to_configured_api_namespace(self):
        settings = voice.Settings()
        self.assertEqual(
            "https://sentinelquantumvanguardaipro.pages.dev/claims/email_verified",
            settings.email_verified_claim,
        )

    def test_malformed_namespace_rejected_when_enabled(self):
        settings = voice.Settings(enabled=True, email_verified_claim="email_verified")
        self.assertIn(
            "VOICE_EMAIL_VERIFIED_CLAIM must be namespaced with https://",
            settings.missing_configuration(
                custom_identity_verifier=True, custom_token_issuer=True
            ),
        )

    def test_oidc_verifier_only_accepts_boolean_true(self):
        settings = voice.Settings(auth_jwks_url="https://example.invalid/jwks")
        verifier = voice.OidcIdentityVerifier.__new__(voice.OidcIdentityVerifier)
        verifier._settings = settings

        class FakeKey:
            key = object()

        class FakeJwks:
            def get_signing_key_from_jwt(self, token):
                return FakeKey()

        verifier._jwks = FakeJwks()
        for value, expected in [
            (True, True), (False, False), ("true", False), (1, False), (None, False)
        ]:
            claims = {
                "sub": "auth0|example",
                settings.email_verified_claim: value,
                "device_ids": ["device-a"],
                settings.entitlement_claim: True,
            }
            with self.subTest(value=value):
                with patch.object(voice.jwt, "decode", return_value=claims):
                    result = asyncio.run(verifier.verify("Bearer fake.jwt.token"))
                self.assertIs(result["email_verified"], expected)


if __name__ == "__main__":
    unittest.main()
