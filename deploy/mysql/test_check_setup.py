import unittest,tempfile,json
from pathlib import Path
from check_setup import inspect,https
class SetupTests(unittest.TestCase):
    def test_urls_reject_insecure_malformed_and_embedded_credentials(self):
        for value in ['',None,'https://','http://example.test','https://name:SECRET@example.test','https://example.test:99999','https://exa mple.test','https://example.test:0','https://example.test/api/wc/policy-previews/privacy']:self.assertFalse(https(value),value)
        self.assertTrue(https('https://example.test/privacy#health'))
    def test_missing_file_is_reported_without_network_or_initialization(self):
        with tempfile.TemporaryDirectory() as folder:
            report=inspect(Path(folder)/'none');self.assertEqual('MISSING',report['checks'][0]['status']);self.assertFalse(list(Path(folder).iterdir()))
    def test_values_are_not_echoed_and_inspection_preserves_files(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);private=root/'private';private.mkdir();(root/'instance.json').write_text('{}')
            app=root/'app.json';app.write_text(json.dumps({'runtime':str(root)}))
            (private/'social-config.json').write_text(json.dumps({'GOOGLE_CLIENT_ID':'secret-marker','GOOGLE_SECRET':'secret-marker'}))
            (private/'service-config.json').write_text(json.dumps({'PUBLIC_MEDICAL_SERVICE_KEY':'secret-marker','TERMS_POLICY_URL':'https://','PRIVACY_POLICY_URL':'https://example.test/privacy'}))
            before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()};report=inspect(app)
            self.assertNotIn('secret-marker',json.dumps(report));self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})
            states={c['id']:c['status'] for c in report['checks']};self.assertEqual('INVALID',states['terms']);self.assertEqual('CONFIGURED',states['google']);self.assertFalse(report['releaseApproved'])
    def test_invalid_or_unknown_configuration_is_redacted(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'private').mkdir();app=root/'app.json';app.write_text(json.dumps({'runtime':str(root)}))
            (root/'private/service-config.json').write_text('{"SECRET-STRING":')
            result=inspect(app);self.assertNotIn('SECRET-STRING',json.dumps(result));self.assertTrue(any(c['id']=='service-config' and c['status']=='INVALID' for c in result['checks']))
    def test_builtin_local_policy_flag_needs_a_valid_base(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'private').mkdir();app=root/'app.json';app.write_text(json.dumps({'runtime':str(root)}))
            service=root/'private/service-config.json';social=root/'private/social-config.json'
            service.write_text(json.dumps({'SKINCARE_BUILTIN_POLICIES':'true'}))
            result={c['id']:c['status'] for c in inspect(app)['checks']};self.assertEqual('CONFIGURED',result['terms'])
            social.write_text(json.dumps({'SOCIAL_PUBLIC_BASE_URL':'http://external.test'}))
            result={c['id']:c['status'] for c in inspect(app)['checks']};self.assertEqual('INVALID',result['terms'])
if __name__=='__main__':unittest.main()
