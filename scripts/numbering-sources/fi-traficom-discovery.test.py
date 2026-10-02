import importlib.util
import unittest
from pathlib import Path
from email.message import Message
spec = importlib.util.spec_from_file_location('discovery', Path(__file__).with_name('fi-traficom-discovery.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
class Response:
    status=200
    def __init__(self, body, media='text/html; charset=utf-8'):
        self.body=body
        self.headers=Message()
        self.headers['Content-Type']=media
    def __enter__(self):return self
    def __exit__(self,*args):pass
    def read(self,size):return self.body[:size]
class Opener:
    def __init__(self,response):self.response=response
    def open(self,*args,**kwargs):return self.response
class Tests(unittest.TestCase):
    def test_only_fixed_official_origins_can_be_fetched(self):
        for url in ['http://tieto.traficom.fi/','https://tieto.traficom.fi.evil.test/','https://user@tieto.traficom.fi/']:
            with self.assertRaisesRegex(ValueError,'SOURCE_NOT_ALLOWED'):module.read_page(url)
    def test_redirects_still_require_review(self):
        with self.assertRaisesRegex(ValueError,'REDIRECT_REQUIRES_REVIEW'):
            module.NoRedirect().redirect_request(None,None,302,'',{},'https://www.traficom.fi/new')
    def test_links_are_candidates_and_external_references_are_never_fetched(self):
        body=b'<a href="https://www.traficom.fi/en/numbering">Numbering</a><a href="https://other.test/api">API</a>'
        page=module.read_page(module.SOURCES['open-data'],Opener(Response(body)))
        self.assertEqual(len(page['links']),1)
        self.assertEqual(len(page['externalReferences']),1)
        self.assertEqual(page['qualification'],'DISCOVERY_ONLY_NOT_PRODUCTION')
        self.assertEqual(len(page['sha256']),64)
    def test_dynamic_swagger_documentation_is_discovery_only_with_bounded_spec_candidates(self):
        body=b'<div id="swagger-ui"></div><script>new SwaggerUi({url:"/swagger/docs/v1"});</script>'
        page=module.read_page(module.SOURCES['fixed-number-ranges-api-documentation'],Opener(Response(body)))
        self.assertEqual(page['apiSpecificationCandidates'],['/swagger/docs/v1'])
        self.assertEqual(page['licenseQualification'],'NUMBERING_DATASET_LICENSE_NOT_YET_VERIFIED')
    def test_error_bodies_size_media_and_encoding_fail_closed(self):
        for response in [Response(b''),Response(b'x'*(module.MAX_BYTES+1)),Response(b'<html>x</html>','application/json'),Response(b'\xff')]:
            with self.assertRaises((ValueError,UnicodeDecodeError)):module.read_page(module.SOURCES['open-data'],Opener(response))
if __name__=='__main__':unittest.main()
