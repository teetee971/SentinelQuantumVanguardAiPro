"""Static regression guard for the approved XML; not Android runtime evidence."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'native-android-app/app/src/main'
ANDROID = '{http://schemas.android.com/apk/res/android}'


class LoopbackPolicyContract(unittest.TestCase):
    def test_manifest_uses_named_config_and_retains_global_false(self):
        app = ET.parse(ROOT / 'AndroidManifest.xml').getroot().find('application')
        self.assertEqual(app.get(ANDROID + 'usesCleartextTraffic'), 'false')
        self.assertEqual(app.get(ANDROID + 'networkSecurityConfig'), '@xml/sentinel_ai_local_network_security')

    def test_global_policy_has_no_extra_overrides_or_trust_changes(self):
        root = ET.parse(ROOT / 'res/xml/sentinel_ai_local_network_security.xml').getroot()
        self.assertEqual(root.tag, 'network-security-config')
        self.assertEqual(root.attrib, {})
        self.assertEqual([n.tag for n in root], ['base-config', 'domain-config'])
        base = root.find('base-config')
        self.assertEqual(base.attrib, {'cleartextTrafficPermitted': 'false'})
        self.assertEqual(list(base), [])

    def test_only_exact_ipv4_loopback_is_excepted(self):
        domain = ET.parse(ROOT / 'res/xml/sentinel_ai_local_network_security.xml').getroot().find('domain-config')
        self.assertEqual(domain.attrib, {'cleartextTrafficPermitted': 'true'})
        self.assertEqual(len(domain), 1)
        host = domain[0]
        self.assertEqual(host.tag, 'domain')
        self.assertEqual(host.attrib, {'includeSubdomains': 'false'})
        self.assertEqual(host.text.strip(), '127.0.0.1')
        self.assertEqual(list(host), [])


if __name__ == '__main__':
    unittest.main()
