import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile
from apk_distribution import prepare, validate_apk, PUBLIC_FILES
from publish_apk import ready_response, read_json, main as publish_main

class DistributionTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.apk=self.root/'synthetic.apk';self.origin='https://synthetic-install-test.trycloudflare.com';self.cert='a'*64
        with zipfile.ZipFile(self.apk,'w') as z:z.writestr('assets/index.android.bundle',self.origin)
        self.meta={'apiOrigin':self.origin,'package':'com.skincareai.mobile','sha256':hashlib.sha256(self.apk.read_bytes()).hexdigest(),'signingCertificate':self.cert,'versionCode':10}
        self.save()
    def save(self):self.apk.with_suffix('.apk.json').write_text(json.dumps(self.meta))
    def test_refuses_different_server_corrupt_apk_and_signing_identity(self):
        validate_apk(self.apk,self.origin,self.cert)
        for origin,cert in [('https://other.trycloudflare.com',self.cert),(self.origin,'b'*64),('http://example.com',self.cert)]:
            with self.assertRaises(ValueError):validate_apk(self.apk,origin,cert)
        self.apk.write_bytes(b'corrupt')
        with self.assertRaises(ValueError):validate_apk(self.apk,self.origin,self.cert)
    def test_qr_decodes_to_install_page_and_public_allowlist_is_exact(self):
        import cv2
        target=self.root/'public';prepare(self.apk,self.origin,target,self.cert)
        decoded,_,_=cv2.QRCodeDetector().detectAndDecode(cv2.imread(str(target/'install-qr.png')))
        self.assertEqual(decoded,self.origin+'/download/')
        self.assertEqual({p.name for p in target.iterdir()},PUBLIC_FILES)
        self.assertEqual((target/'SkinCare-AI.apk').read_bytes(),self.apk.read_bytes())
        with self.assertRaises(ValueError):prepare(self.apk,self.origin,target,self.cert)
    def test_temporary_maintenance_gate_retries_but_missing_asset_does_not(self):
        unavailable=urllib.error.HTTPError(self.origin,503,'maintenance',{},None)
        with patch('publish_apk.urllib.request.urlopen',side_effect=[unavailable,'ready']) as request, patch('publish_apk.time.sleep'):
            self.assertEqual(ready_response(self.origin),'ready');self.assertEqual(request.call_count,2)
        missing=urllib.error.HTTPError(self.origin,404,'missing',{},None)
        with patch('publish_apk.urllib.request.urlopen',side_effect=missing) as request, patch('publish_apk.time.sleep'):
            with self.assertRaises(urllib.error.HTTPError):ready_response(self.origin)
            self.assertEqual(request.call_count,1)

    def publication_fixture(self):
        runtime=self.root/'비회원 배포';bundle=runtime/'bundle';bundle.mkdir(parents=True)
        output=self.root/'output';output.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'runtime':str(runtime),'output':str(output)},ensure_ascii=False),encoding='utf-8-sig')
        (output/'public-connection.json').write_text(json.dumps({'active':True,'origin':self.origin}),encoding='utf-8')
        manifest={'deploymentMode':'temporary-tunnel','project':'skincare-free-demo-12345678','progressRelease':'verified','description':'기존 배포 보존'}
        (bundle/'bundle-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False),encoding='utf-8')
        assets=self.root/'public';prepare(self.apk,self.origin,assets,self.cert)
        checked=self.root/'verification.json';checked.write_text(json.dumps({'origin':self.origin,'allPassed':True,'sha256':self.meta['sha256'],'certificateSha256':self.cert,'versionCode':10}),encoding='utf-8')
        diagnostic=output/'attempt.json'
        return runtime,checked,diagnostic,['--config',str(config),'--assets',str(assets),'--verification',str(checked),'--diagnostic-output',str(diagnostic),'--check-only']

    def test_utf8_and_powershell_bom_ignore_legacy_windows_code_page(self):
        real_read=Path.read_text
        def windows_read(path,*args,**kwargs):
            kwargs.setdefault('encoding','cp949')
            return real_read(path,*args,**kwargs)
        for encoding in ('utf-8','utf-8-sig'):
            source=self.root/'unicode.json';source.write_text('{"label":"비회원 경과 사진"}',encoding=encoding)
            with patch.object(Path,'read_text',windows_read):
                self.assertEqual(read_json(source),{'label':'비회원 경과 사진'})

    def test_preflight_with_korean_paths_makes_no_runtime_or_docker_changes(self):
        runtime,_,diagnostic,args=self.publication_fixture()
        before={p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()}
        with patch('publish_apk.subprocess.run') as docker,patch('publish_apk.instance_lock') as lock:
            publish_main(args);docker.assert_not_called();lock.assert_not_called()
        self.assertEqual(before,{p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()})
        result=read_json(diagnostic);self.assertTrue(result['passed']);self.assertTrue(result['preflightOnly']);self.assertFalse(result['publicationAttempted'])

    def test_bad_encoding_records_safe_failure_before_docker(self):
        runtime,checked,diagnostic,args=self.publication_fixture()
        checked.write_bytes(b'{"private":"do-not-log-this-\xff"}')
        before={p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()}
        with patch('publish_apk.subprocess.run') as docker:
            with self.assertRaises(UnicodeDecodeError):publish_main(args)
            docker.assert_not_called()
        result=read_json(diagnostic);self.assertFalse(result['passed']);self.assertFalse(result['publicationAttempted'])
        self.assertEqual(result['inputFile'],'verification.json');self.assertEqual(result['errorType'],'UnicodeDecodeError')
        self.assertNotIn('do-not-log-this',diagnostic.read_text(encoding='utf-8'))
        self.assertEqual(before,{p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()})

    def test_corrupt_download_still_fails_preflight(self):
        _,_,diagnostic,args=self.publication_fixture()
        (self.root/'public/SkinCare-AI.apk').write_bytes(b'corrupt')
        with patch('publish_apk.subprocess.run') as docker:
            with self.assertRaisesRegex(RuntimeError,'checksum'):publish_main(args)
            docker.assert_not_called()
        self.assertFalse(read_json(diagnostic)['passed'])

if __name__=='__main__':unittest.main()
