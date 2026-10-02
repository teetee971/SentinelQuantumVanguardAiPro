"""Read-only discovery of current ITU references; never produces production records."""
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

SOURCES = {
    'national-numbering-plans': 'https://www.itu.int/oth/T0202.aspx?parent=T0202',
    'e164-recommendation': 'https://www.itu.int/rec/T-REC-E.164/en',
    'international-country-codes': 'https://www.itu.int/en/publications/ITU-T/Pages/publications.aspx?parent=T-SP&view=T-SP2',
    'universal-numbers': 'https://www.itu.int/en/ITU-T/inr/unum/Pages/default.aspx',
    'operational-bulletins': 'https://www.itu.int/pub/T-SP-OB',
    'assigned-codes-baseline-publication': 'https://www.itu.int/pub/T-SP-E.164D-2016',
    'global-network-codes': 'https://www.itu.int/oth/T0207000001/en',
    'finland-plan': 'https://www.itu.int/oth/T0202000049/en',
    'czech-plan': 'https://www.itu.int/oth/T0202000035/en',
    'international-numbering-resources': 'https://www.itu.int/en/ITU-T/inr/Pages/default.aspx',
}
MAX_BYTES = 2 * 1024 * 1024
MAX_ITEMS = 2000

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('ITU_REDIRECT_REQUIRES_REVIEW: ' + newurl)

class Discovery(HTMLParser):
    def __init__(self, base):
        super().__init__(convert_charrefs=True)
        self.base = base
        self.rows = []
        self.links = []
        self.external_references = []
        self.row = None
        self.link = None
        self.suppressed = 0

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ('script', 'style'):
            self.suppressed += 1
        if tag == 'tr':
            self.row = {'text': [], 'links': []}
        if tag == 'a' and 'href' in attrs:
            raw = urljoin(self.base, attrs['href'])
            parsed = urlsplit(raw)
            if parsed.scheme in ('https', 'http') and not parsed.username and not parsed.password and not parsed.port:
                self.link = {'url': raw, 'text': [], 'officialHttps': parsed.scheme == 'https' and parsed.hostname == 'www.itu.int'}

    def handle_data(self, data):
        if self.suppressed:
            return
        if self.row is not None:
            self.row['text'].append(data)
        if self.link is not None:
            self.link['text'].append(data)

    def handle_endtag(self, tag):
        if tag in ('script', 'style'):
            self.suppressed = max(0, self.suppressed - 1)
        if tag == 'a' and self.link is not None:
            item = {'url': self.link['url'], 'text': ' '.join(' '.join(self.link['text']).split())}
            if self.link['officialHttps']:
                self.links.append(item)
                if self.row is not None:
                    self.row['links'].append(item)
            else:
                self.external_references.append({**item, 'qualification': 'SOURCE_DECLARED_REFERENCE_NOT_FETCHED'})
            self.link = None
        if tag == 'tr' and self.row is not None:
            self.rows.append({'text': ' '.join(' '.join(self.row['text']).split()), 'links': self.row['links']})
            self.row = None
        if len(self.rows) > MAX_ITEMS or len(self.links) + len(self.external_references) > MAX_ITEMS:
            raise ValueError('ITU_STRUCTURE_LIMIT')

def read_page(url, opener=None):
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname != 'www.itu.int' or parsed.username or parsed.password or parsed.port:
        raise ValueError('ITU_SOURCE_NOT_ALLOWED')
    opener = opener or build_opener(NoRedirect())
    request = Request(url, headers={'User-Agent': 'Sentinel/1.0 official-numbering-reference-audit'})
    with opener.open(request, timeout=20) as response:
        if response.status != 200:
            raise ValueError('ITU_HTTP_ERROR')
        if int(response.headers.get('Content-Length', '0')) > MAX_BYTES:
            raise ValueError('ITU_PAGE_TOO_LARGE')
        if 'text/html' not in response.headers.get('Content-Type', '').lower():
            raise ValueError('ITU_UNEXPECTED_MEDIA_TYPE')
        body = response.read(MAX_BYTES + 1)
        if not body or len(body) > MAX_BYTES:
            raise ValueError('ITU_PAGE_SIZE_INVALID')
        charset = response.headers.get_content_charset()
        if not charset:
            meta = re.search(br'charset\s*=\s*[\"\']?([a-zA-Z0-9_-]+)', body[:4096], re.IGNORECASE)
            charset = meta.group(1).decode('ascii') if meta else 'utf-8'
        if charset.lower() not in ('utf-8', 'utf8', 'windows-1252', 'iso-8859-1'):
            raise ValueError('ITU_ENCODING_REQUIRES_REVIEW')
        text = body.decode(charset, errors='strict')
    parser = Discovery(url)
    parser.feed(text)
    if not parser.links:
        raise ValueError('ITU_NO_REFERENCE_LINKS')
    return {'sourceUrl': url, 'fetchedAt': datetime.now(timezone.utc).isoformat(),
            'sha256': hashlib.sha256(body).hexdigest(), 'bytes': len(body), 'encoding': charset,
            'rows': parser.rows, 'links': parser.links, 'externalReferences': parser.external_references,
            'qualification': 'DISCOVERY_ONLY_NOT_PRODUCTION'}

def main():
    failed = False
    for name, url in SOURCES.items():
        try:
            page = read_page(url)
            # One JSON object per line keeps the evidence recoverable from job logs.
            print('ITU_REFERENCE_EVIDENCE ' + json.dumps({'name': name, **page}, ensure_ascii=True))
        except Exception as error:
            failed = True
            print('ITU_REFERENCE_FAILURE ' + json.dumps({'name': name, 'url': url, 'error': str(error)}))
    return int(failed)

if __name__ == '__main__':
    sys.exit(main())
