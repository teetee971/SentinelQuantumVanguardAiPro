import importlib.util
import unittest
import time
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
        text = 'National Numbering Plans<table><tr><td>- A - ITU original &amp; name (+1) - GN -</td><td><a href="/oth/default.aspx?lang=en&amp;parent=T0202000001">Plan</a></td></tr></table><a href="https://www.itu.int.evil.test/x">Fake</a><script>untrusted()</script>'
        result = audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(Response(text.encode())))
        self.assertEqual(result['rows'][0]['text'], '- A - ITU original & name (+1) - GN - Plan')
        self.assertEqual(len(result['links']), 1)
        self.assertEqual(result['qualification'], 'DISCOVERY_ONLY_NOT_PRODUCTION')
        self.assertEqual(len(result['sha256']), 64)
    def test_foreign_hosts_and_credentials_are_rejected(self):
        for url in ['http://www.itu.int/', 'https://evil.test/', 'https://www.itu.int.evil.test/', 'https://x@www.itu.int/', 'https://www.itu.int:0/', 'https://@www.itu.int/']:
            with self.assertRaisesRegex(ValueError, 'ITU_SOURCE_NOT_ALLOWED'): audit.read_page(url)
    def test_size_media_type_encoding_and_empty_are_rejected(self):
        cases = [(Response(b''), 'ITU_PAGE_SIZE_INVALID'), (Response(b'x'*(audit.MAX_BYTES+1)), 'ITU_PAGE_SIZE_INVALID'), (Response(b'x', declared=audit.MAX_BYTES+1), 'ITU_PAGE_TOO_LARGE'), (Response(b'x', 'application/json'), 'ITU_UNEXPECTED_MEDIA_TYPE'), (Response(b'x', 'application/x-text/html'), 'ITU_UNEXPECTED_MEDIA_TYPE')]
        for response, reason in cases:
            with self.assertRaisesRegex(ValueError, reason): audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(response))
        with self.assertRaises(UnicodeDecodeError): audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(Response(b'\xff')))
    def test_navigation_only_error_page_cannot_qualify_as_a_numbering_reference(self):
        with self.assertRaisesRegex(ValueError, 'ITU_SOURCE_STRUCTURE_CHANGED'):
            audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(Response(b'<a href="/">ITU home</a>Maintenance')))
    def test_overall_deadline_interrupts_a_slow_body_despite_socket_activity(self):
        class Slow(Response):
            def read(self, size):
                time.sleep(0.1)
                return b'<a href="/">ITU</a>'
        with self.assertRaisesRegex(TimeoutError, 'ITU_FETCH_DEADLINE_EXCEEDED'):
            audit.read_page(audit.SOURCES['national-numbering-plans'], Opener(Slow(b'')), deadline_seconds=0.01)
    def test_explicit_html_meta_encoding_is_respected_when_http_charset_is_absent(self):
        body = b'<meta charset=windows-1252>Recommendation E.164 In force components<table><tr><td><a href="/rec">E.164\xa0(02/26)</a> Title In force</td></tr></table>'
        result = audit.read_page(audit.SOURCES['e164-recommendation'], Opener(Response(body, 'text/html')))
        self.assertEqual(result['encoding'], 'windows-1252')
        self.assertEqual(result['links'][0]['text'], 'E.164 (02/26)')
    def test_redirects_require_review(self):
        with self.assertRaisesRegex(ValueError, 'ITU_REDIRECT_REQUIRES_REVIEW'):
            audit.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://www.itu.int/other')
    def test_structure_limits(self):
        parser = audit.Discovery('https://www.itu.int/')
        with self.assertRaisesRegex(ValueError, 'ITU_STRUCTURE_LIMIT'): parser.feed('<a href="/x">x</a>'*(audit.MAX_ITEMS+1))

if __name__ == '__main__': unittest.main()
