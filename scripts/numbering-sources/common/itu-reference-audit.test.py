import importlib.util
import unittest
from email.message import Message
from pathlib import Path

spec = importlib.util.spec_from_file_location('itu_audit', Path(__file__).with_name('itu-reference-audit.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class Response:
    status = 200
    def __init__(self, body, content_type='text/html; charset=utf-8', declared=None):
        self.body = body
        self.headers = Message()
        self.headers['Content-Type'] = content_type
        if declared is not None:
            self.headers['Content-Length'] = str(declared)
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, size): return self.body[:size]

class Opener:
    def __init__(self, response): self.response = response
    def open(self, request, timeout):
        assert timeout == 20
        return self.response

class Tests(unittest.TestCase):
    def test_discovery_preserves_original_designations_and_official_links(self):
        text = '<table><tr><td>ITU original &amp; name +1</td><td><a href="/oth/T0202000001/en">Plan</a></td></tr></table><a href="https://www.itu.int.evil.test/x">Fake</a><script>untrusted()</script>'
        result = audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(Response(text.encode())))
        self.assertEqual(result['rows'][0]['text'], 'ITU original & name +1 Plan')
        self.assertEqual(len(result['links']), 1)
        self.assertEqual(result['qualification'], 'DISCOVERY_ONLY_NOT_PRODUCTION')
        self.assertEqual(len(result['sha256']), 64)
    def test_foreign_hosts_and_credentials_are_rejected(self):
        for url in ['http://www.itu.int/', 'https://evil.test/', 'https://www.itu.int.evil.test/', 'https://x@www.itu.int/']:
            with self.assertRaisesRegex(ValueError, 'ITU_SOURCE_NOT_ALLOWED'): audit.read_page(url)
    def test_size_media_type_encoding_and_empty_are_rejected(self):
        for response in [Response(b''), Response(b'x'*(audit.MAX_BYTES+1)), Response(b'x', declared=audit.MAX_BYTES+1), Response(b'x', 'application/json'), Response(b'\xff')]:
            with self.assertRaises((ValueError, UnicodeDecodeError)): audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(response))
    def test_redirects_require_review(self):
        with self.assertRaisesRegex(ValueError, 'ITU_REDIRECT_REQUIRES_REVIEW'):
            audit.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://www.itu.int/other')
    def test_structure_limits(self):
        parser = audit.Discovery('https://www.itu.int/')
        with self.assertRaisesRegex(ValueError, 'ITU_STRUCTURE_LIMIT'): parser.feed('<a href="/x">x</a>'*(audit.MAX_ITEMS+1))

if __name__ == '__main__': unittest.main()
