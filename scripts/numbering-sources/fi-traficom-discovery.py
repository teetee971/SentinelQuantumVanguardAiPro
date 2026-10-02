"""Read-only Traficom source discovery; never produces a production numbering dataset."""
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

SOURCES = {
    'open-data': 'https://tieto.traficom.fi/en/open-data',
    'fixed-number-ranges-api-documentation': 'https://opendata.traficom.fi/swagger/ui/index#/KiinteanPuhelinverkonTilaajanumerot',
}
ALLOWED_HOSTS = {'tieto.traficom.fi', 'static.traficom.fi', 'www.traficom.fi', 'opendata.traficom.fi'}

MAX_BYTES = 2 * 1024 * 1024
MAX_ITEMS = 2000

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('TRAFICOM_REDIRECT_REQUIRES_REVIEW: ' + newurl)

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
        self.visible_text = []

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
                self.link = {'url': raw, 'text': [], 'officialHttps': parsed.scheme == 'https' and parsed.hostname in ALLOWED_HOSTS}

    def handle_data(self, data):
        if self.suppressed:
            return
        self.visible_text.append(data)
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
            raise ValueError('TRAFICOM_STRUCTURE_LIMIT')

def read_page(url, opener=None):
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname not in ALLOWED_HOSTS or parsed.username or parsed.password or parsed.port:
        raise ValueError('TRAFICOM_SOURCE_NOT_ALLOWED')
    opener = opener or build_opener(NoRedirect())
    request = Request(url, headers={'User-Agent': 'Sentinel/1.0 official-traficom-source-discovery'})
    with opener.open(request, timeout=20) as response:
        if response.status != 200:
            raise ValueError('TRAFICOM_HTTP_ERROR')
        if int(response.headers.get('Content-Length', '0')) > MAX_BYTES:
            raise ValueError('TRAFICOM_PAGE_TOO_LARGE')
        if 'text/html' not in response.headers.get('Content-Type', '').lower():
            raise ValueError('TRAFICOM_UNEXPECTED_MEDIA_TYPE')
        body = response.read(MAX_BYTES + 1)
        if not body or len(body) > MAX_BYTES:
            raise ValueError('TRAFICOM_PAGE_SIZE_INVALID')
        charset = response.headers.get_content_charset()
        if not charset:
            meta = re.search(br'charset\s*=\s*[\"\']?([a-zA-Z0-9_-]+)', body[:4096], re.IGNORECASE)
            charset = meta.group(1).decode('ascii') if meta else 'utf-8'
        if charset.lower() not in ('utf-8', 'utf8', 'windows-1252', 'iso-8859-1'):
            raise ValueError('TRAFICOM_ENCODING_REQUIRES_REVIEW')
        text = body.decode(charset, errors='strict')
    parser = Discovery(url)
    parser.feed(text)
    swagger_page = urlsplit(url).hostname == 'opendata.traficom.fi' and ('SwaggerUi' in text or 'swagger-ui' in text.lower())
    if not parser.links and not swagger_page:
        raise ValueError('TRAFICOM_NO_REFERENCE_LINKS')
    api_specs = re.findall(r'''url\s*:\s*["']([^"']{1,500})["']''', text) if swagger_page else []
    return {'sourceUrl': url, 'fetchedAt': datetime.now(timezone.utc).isoformat(),
            'sha256': hashlib.sha256(body).hexdigest(), 'bytes': len(body), 'encoding': charset,
            'rows': parser.rows, 'links': parser.links, 'externalReferences': parser.external_references,
            'visibleText': ' '.join(' '.join(parser.visible_text).split())[:25000],
            'apiSpecificationCandidates': api_specs,
            'licenseQualification': 'NUMBERING_DATASET_LICENSE_NOT_YET_VERIFIED',
            'legacyLicenceReference': {'url': 'https://static.traficom.fi/en/transport-system/geoinformationsmaterial/use-and-licences-data', 'observedRedirect': 'https://www.traficom.fi/', 'observedAt': '2026-10-02', 'qualification': 'OBSOLETE_REFERENCE_NOT_NUMBERING_LICENSE'},
            'qualification': 'DISCOVERY_ONLY_NOT_PRODUCTION'}

def main():
    failed = False
    for name, url in SOURCES.items():
        try:
            page = read_page(url)
            # One JSON object per line keeps the evidence recoverable from job logs.
            print('TRAFICOM_SOURCE_EVIDENCE ' + json.dumps({'name': name, **page}, ensure_ascii=True))
        except Exception as error:
            failed = True
            print('TRAFICOM_SOURCE_FAILURE ' + json.dumps({'name': name, 'url': url, 'error': str(error)}))
    return int(failed)

if __name__ == '__main__':
    sys.exit(main())
