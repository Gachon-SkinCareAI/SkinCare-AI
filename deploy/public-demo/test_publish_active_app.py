import hashlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from publish_active_app import publish, verify_snapshot
from run_free_demo import checksum


class ActiveAppTests(unittest.TestCase):
    def fixture(self, root):
        origin = 'https://native-app-fixture.trycloudflare.com'
        bundle = root/'bundle'
        folder = bundle/'public-web/web-123'
        (folder/'web').mkdir(parents=True)
        (folder/'web/index.html').write_text('old web')
        (folder/'Caddyfile').write_text('old caddy')
        (folder/'compose.public-web.json').write_text('{}')
        files = {p.relative_to(folder).as_posix():checksum(p) for p in folder.rglob('*') if p.is_file()}
        (folder/'release.json').write_text(json.dumps({'origin':origin,'files':files}))
        receipt = {'release':'web-123','origin':origin,'releaseSha256':checksum(folder/'release.json')}
        (bundle/'public-web-active.json').write_text(json.dumps(receipt))
        (bundle/'bundle-manifest.json').write_text('{"project":"fixture"}')
        (bundle/'.env').write_text('preserved-private-fixture')
        (root/'config.json').write_text(json.dumps({'format':'skincare-free-demo-v1','runtime':str(root)}))
        (root/'status.json').write_text(json.dumps({'active':True,'passed':True,'origin':origin}))
        source = root/'assets'
        source.mkdir()
        apk = b'signed fixture stand-in'
        (source/'SkinCare-AI.apk').write_bytes(apk)
        (source/'install-qr.png').write_bytes(b'qr fixture')
        (source/'index.html').write_text('SkinCare-AI.apk')
        release = {'apiOrigin':origin,'package':'com.skincareai.mobile','versionCode':14,
                   'sha256':hashlib.sha256(apk).hexdigest(),'signingCertificate':'fixture-cert',
                   'bytes':len(apk),'downloadUrl':origin+'/download/SkinCare-AI.apk'}
        (source/'release.json').write_text(json.dumps(release))
        (root/'verified.json').write_text(json.dumps({'allPassed':True,'origin':origin,
            'sha256':release['sha256'],'certificateSha256':'fixture-cert','versionCode':14}))
        args = SimpleNamespace(config=root/'config.json', public_status=root/'status.json',
            assets=source, verification=root/'verified.json', output=root/'out', check_only=False)
        return args, folder, receipt, origin

    def execute_case(self, change=None, fail_download=False, stop_after_validate=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args, folder, receipt, origin = self.fixture(root)
            protected = {n:(root/'bundle'/n).read_bytes() for n in ('bundle-manifest.json','.env')}
            calls = []
            if change:
                change(root, args, folder)
            def execute(argv, **kwargs):
                calls.append(argv)
                if stop_after_validate and 'validate' in argv:
                    (root/'public-web-stop.request').touch()
                return SimpleNamespace(returncode=0,stdout=b'ok',stderr=b'')
            def public_check(actual_origin, release, qr_hash):
                self.assertEqual(actual_origin, origin)
                self.assertEqual(checksum(folder/'web/download/SkinCare-AI.apk'),release['sha256'])
                self.assertEqual(checksum(folder/'web/download/install-qr.png'),qr_hash)
                if fail_download:
                    raise RuntimeError('public bytes mismatch')
            cv2 = SimpleNamespace(imread=lambda _:None,
                QRCodeDetector=lambda:SimpleNamespace(detectAndDecode=lambda _:(origin+'/download/',None,None)))
            with patch.dict(sys.modules,{'cv2':cv2}), patch('publish_active_app.validate_active',return_value=receipt), \
                    patch('publish_active_app.load_rollback_record'), \
                    patch('publish_active_app.compose_command',return_value=['docker','compose']), \
                    patch('publish_active_app.HERE',Path(__file__).resolve().parent):
                error = None
                try:
                    result = publish(args,public_check=public_check,execute=execute)
                except RuntimeError as exc:
                    error = str(exc)
                    result = json.loads((root/'out/publication.json').read_text()) if (root/'out/publication.json').exists() else {}
            for name, saved in protected.items():
                self.assertEqual((root/'bundle'/name).read_bytes(),saved)
            for command in calls:
                self.assertFalse(any(service in command for service in ('ai','llm','backend','db')))
                self.assertNotIn('down',command)
            if error is None:
                saved_receipt = json.loads((root/'bundle/public-web-active.json').read_text())
                verify_snapshot(folder,saved_receipt)
                self.assertTrue((folder/'web/download/SkinCare-AI.apk').is_file())
            elif fail_download or stop_after_validate:
                self.assertEqual((folder/'Caddyfile').read_text(),'old caddy')
                self.assertFalse((folder/'web/download').exists())
                verify_snapshot(folder,receipt)
            return result, calls, error

    def test_publishes_same_origin_with_only_edge_recreated(self):
        result, calls, error = self.execute_case()
        self.assertIsNone(error)
        self.assertTrue(result['passed'] and result['fullApkDownloadVerified'])
        self.assertEqual(sum('--force-recreate' in c for c in calls),1)
        self.assertTrue(result['rollbackPreserved'])

    def test_failed_download_restores_previous_route_and_receipts(self):
        result, calls, error = self.execute_case(fail_download=True)
        self.assertIn('public bytes mismatch',error)
        self.assertTrue(result['previousPublicRouteRestored'])
        self.assertEqual(sum('--force-recreate' in c for c in calls),2)

    def test_stopped_owner_cannot_be_reopened_by_publisher(self):
        result, calls, error = self.execute_case(stop_after_validate=True)
        self.assertIn('stopped or changed',error)
        self.assertFalse(any('--force-recreate' in c for c in calls))

    def test_different_or_stopped_origin_is_rejected_before_docker(self):
        def change(root,args,folder):
            args.public_status.write_text('{"active":false,"origin":"https://different.trycloudflare.com"}')
        _, calls, error = self.execute_case(change=change)
        self.assertIsNotNone(error)
        self.assertFalse(calls)

    def test_changed_active_release_is_not_overwritten(self):
        def change(root,args,folder):
            (folder/'Caddyfile').write_text('unreviewed change')
        _, calls, error = self.execute_case(change=change)
        self.assertIn('file differs',error)
        self.assertFalse(calls)

    def test_changed_apk_is_not_published(self):
        def change(root,args,folder):
            (args.assets/'SkinCare-AI.apk').write_bytes(b'wrong APK')
        _, calls, error = self.execute_case(change=change)
        self.assertIn('checksum mismatch',error)
        self.assertFalse(calls)


if __name__=='__main__':
    unittest.main()
