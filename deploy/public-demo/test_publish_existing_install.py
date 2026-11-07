import copy
import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from develop_rollback import FILES, checksum, load_rollback_record, save_rollback_record
from publish_existing_install import publish, verify_previous_api


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())


def jar(path, api=b'unchanged', legacy=b'legacy'):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('BOOT-INF/classes/skincare/controller/AnalysisController.class', api)
        archive.writestr('BOOT-INF/classes/skincare/service/QuestionnaireService.class', legacy)


class ExistingInstallTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'bundle'
        self.operation = self.root / 'develop-upgrade'
        self.old_backup = self.operation / 'before-123'
        self.old_backup.mkdir(parents=True)
        self.origin = 'https://fixture.trycloudflare.com'
        self.project = 'skincare-free-demo-1234abcd'
        self.assets = self.root / 'candidate'
        self.old = self.assets_at(self.bundle / 'distribution', 12, b'old APK')
        self.new = self.assets_at(self.assets, 13, b'new APK')
        overlay = {'services': {'edge': {'environment': {'DISTRIBUTION_HOST': 'fixture.trycloudflare.com'},
                                        'volumes': ['./distribution:/srv/distribution:ro']}}}
        write(self.bundle / 'compose.distribution.yaml', overlay)
        write(self.bundle / 'compose.yaml', b'unchanged compose')
        write(self.bundle / 'Caddyfile.tunnel', b'unchanged download route')
        write(self.bundle / 'migrations/V008__capture_date_and_symptoms.sql', b'unchanged schema')
        write(self.bundle / '.env', b'private configuration')
        original = {'project': self.project, 'deploymentMode': 'temporary-tunnel', 'distributionEnabled': True,
                    'distributionRelease': self.old, 'sources': {name: checksum(self.bundle / name)
                        for name in ('Caddyfile.tunnel', 'compose.distribution.yaml')}}
        write(self.old_backup / 'bundle-manifest.json', original)
        server = {'id': 'test-develop-server', 'origin': self.origin, 'project': self.project,
                  'files': {}, 'baseFiles': {}, 'protectedFiles': {}}
        for name in FILES:
            if name.endswith('.jar'):
                jar(self.old_backup / name, legacy=b'old compatibility')
                jar(self.bundle / name, legacy=b'new compatibility')
            else:
                write(self.old_backup / name, b'old code')
                write(self.bundle / name, b'new code')
            server['files'][name] = checksum(self.bundle / name)
            server['baseFiles'][name] = checksum(self.old_backup / name)
        for file in self.bundle.rglob('*'):
            if file.is_file() and file.relative_to(self.bundle).as_posix() not in FILES:
                server['protectedFiles'][file.relative_to(self.bundle).as_posix()] = checksum(file)
        images = {service: {'id': 'sha256:' + letter * 64, 'tag': self.project + '-' + service,
                           'backupTag': self.project + '-' + service + ':before-develop-123',
                           'containerImageDigest': 'sha256:' + letter * 64}
                  for service, letter in [('ai', 'a'), ('backend', 'b')]}
        self.record = save_rollback_record(self.old_backup, server, images, {})
        write(self.bundle / 'bundle-manifest.json', self.record['newManifest'])
        write(self.root / 'public-connection.json', {'active': True, 'origin': self.origin, 'publicApkVersionCode': 12})
        write(self.root / 'config.json', {'format': 'skincare-free-demo-v1', 'runtime': str(self.root), 'output': str(self.root)})
        self.args = SimpleNamespace(config=str(self.root / 'config.json'), assets=str(self.assets),
                                    verification=str(self.root / 'verification.json'), report=str(self.root / 'report.json'), check_only=False)
        self.verification()

    def assets_at(self, folder, version, apk):
        release = {'apiOrigin': self.origin, 'package': 'com.skincareai.mobile', 'versionCode': version,
                   'signingCertificate': 'c' * 64, 'bytes': len(apk), 'sha256': hashlib.sha256(apk).hexdigest()}
        for name, value in [('SkinCare-AI.apk', apk), ('index.html', ('SkinCare-AI.apk ' + release['sha256']).encode()),
                            ('install-qr.png', b'unchanged QR'), ('release.json', release)]:
            write(folder / name, value)
        return release

    def verification(self):
        write(self.root / 'verification.json', {'origin': self.origin, 'allPassed': True, 'sha256': self.new['sha256'],
                                               'certificateSha256': self.new['signingCertificate'], 'versionCode': self.new['versionCode']})

    def public_check(self, origin, release):
        self.assertEqual(origin, self.origin)
        self.assertEqual(checksum(self.bundle / 'distribution/SkinCare-AI.apk'), release['sha256'])
        self.assertEqual(json.loads((self.bundle / 'distribution/release.json').read_text()), release)

    def test_publish_refreshes_rollback_preserving_original_server_and_new_apk(self):
        old_record = (self.old_backup / 'rollback.json').read_bytes()
        report = publish(self.args, self.public_check)
        self.assertTrue(report['passed'])
        self.assertFalse(report['serverRestarted'])
        backup, record, original = load_rollback_record(self.bundle, self.operation)
        self.assertNotEqual(backup, self.old_backup)
        self.assertEqual(record['oldFiles'], self.record['oldFiles'])
        self.assertEqual(record['images'], self.record['images'])
        self.assertEqual(original['distributionRelease'], self.new)
        self.assertEqual(record['newManifest']['distributionRelease'], self.new)
        self.assertEqual(old_record, (self.old_backup / 'rollback.json').read_bytes())
        self.assertEqual(json.loads((self.root / 'public-connection.json').read_text())['publicApkVersionCode'], 13)
        repeated = publish(self.args, self.public_check)
        self.assertTrue(repeated['alreadyApplied'])
        self.assertEqual(backup, load_rollback_record(self.bundle, self.operation)[0])

    def test_check_only_does_not_replace_any_server_or_distribution_file(self):
        before = {p.relative_to(self.bundle).as_posix(): checksum(p) for p in self.bundle.rglob('*') if p.is_file()}
        self.args.check_only = True
        self.assertTrue(publish(self.args, self.public_check)['checkOnly'])
        self.assertTrue(all(checksum(self.bundle / name) == digest for name, digest in before.items()))
        self.assertEqual(load_rollback_record(self.bundle, self.operation)[0], self.old_backup)

    def test_public_verification_failure_restores_old_assets(self):
        def failed(origin, release):
            if release['versionCode'] == 13:
                raise RuntimeError('synthetic public verification failure')
            self.public_check(origin, release)
        with self.assertRaises(RuntimeError):
            publish(self.args, failed)
        self.public_check(self.origin, self.old)
        self.assertEqual(load_rollback_record(self.bundle, self.operation)[0], self.old_backup)
        self.assertTrue(json.loads((self.root / 'report.json').read_text())['previousAssetsRestored'])

    def test_failure_after_new_rollback_pointer_restores_pointer_and_manifest(self):
        import publish_existing_install as module
        real = module.json_write
        def interrupted(path, value):
            if path == self.bundle / 'bundle-manifest.json':
                raise RuntimeError('synthetic interruption')
            return real(path, value)
        with patch.object(module, 'json_write', side_effect=interrupted):
            with self.assertRaises(RuntimeError):
                publish(self.args, self.public_check)
        self.public_check(self.origin, self.old)
        self.assertEqual(load_rollback_record(self.bundle, self.operation)[0], self.old_backup)

    def test_other_signer_or_version_downgrade_is_rejected(self):
        for change in ({'signingCertificate': 'd' * 64}, {'versionCode': 11}):
            original = copy.deepcopy(self.new)
            self.new.update(change)
            write(self.assets / 'release.json', self.new)
            self.verification()
            with self.assertRaises(RuntimeError):
                publish(self.args, self.public_check)
            self.new = original
        self.public_check(self.origin, self.old)

    def test_changed_public_api_is_not_treated_as_rollback_compatible(self):
        other = self.root / 'other.jar'
        jar(other, api=b'changed endpoint')
        with self.assertRaises(RuntimeError):
            verify_previous_api(self.old_backup / 'backend/app.jar', other)

    def test_changed_route_configuration_stops_before_asset_replacement(self):
        write(self.bundle / 'Caddyfile.tunnel', b'changed route')
        with self.assertRaises(RuntimeError):
            publish(self.args, self.public_check)
        self.public_check(self.origin, self.old)


if __name__ == '__main__':
    unittest.main()
