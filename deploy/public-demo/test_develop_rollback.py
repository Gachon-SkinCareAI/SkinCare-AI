import json
from pathlib import Path
import shutil
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from develop_rollback import (FILES, checksum, load_rollback_record, restore_services,
                              save_rollback_record, upgraded_manifest)
import rollback_develop_services as cli
import update_develop_services as updater


class RollbackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'bundle'
        self.operation = self.root / 'develop-upgrade'
        self.backup = self.operation / 'before-123'
        self.backup.mkdir(parents=True)
        self.bundle.mkdir()
        self.project = 'skincare-free-demo-1234abcd'
        self.original = {'project': self.project, 'deploymentMode': 'temporary-tunnel', 'sources': {}, 'backendSha256': 'old'}
        for directory in (self.bundle, self.backup):
            (directory / 'bundle-manifest.json').write_text(json.dumps(self.original), encoding='utf-8')
        self.release = {'id': 'fixture-v1', 'project': self.project, 'origin': 'https://fixture.trycloudflare.com',
                        'files': {}, 'baseFiles': {}, 'protectedFiles': {}}
        for name in FILES:
            for directory, content in ((self.backup, b'old'), (self.bundle, b'new')):
                file = directory / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(content)
            self.release['files'][name] = checksum(self.bundle / name)
            self.release['baseFiles'][name] = checksum(self.backup / name)
        for name in ('compose.yaml', 'migrations/V008__capture_date_and_symptoms.sql', 'distribution/SkinCare-AI.apk'):
            file = self.bundle / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b'keep')
            self.release['protectedFiles'][name] = checksum(file)
        (self.bundle / '.env').write_text('fixture-only', encoding='utf-8')
        self.images = {service: {'id': 'sha256:' + char * 64, 'tag': self.project + '-' + service + ':latest',
                                'backupTag': self.project + '-' + service + ':before-develop-123',
                                'containerImageDigest': 'sha256:' + char * 64}
                       for service, char in [('ai', 'a'), ('backend', 'b')]}
        self.record = save_rollback_record(self.backup, self.release, self.images, {'.env': checksum(self.bundle / '.env')})
        (self.bundle / 'bundle-manifest.json').write_text(json.dumps(self.record['newManifest']), encoding='utf-8')
        self.commands = []
        self.failure = None
        self.compose = ['docker', 'compose', '-p', self.project]
        self.config = self.root / 'launcher-config.json'
        self.config.write_text(json.dumps({'format': 'skincare-free-demo-v1', 'runtime': str(self.root), 'output': str(self.root)}))
        (self.root / 'public-connection.json').write_text(json.dumps({'active': True, 'origin': self.release['origin']}))

    def command(self, argv, name, timeout=180, persist=True):
        self.commands.append((argv, name))
        if name == self.failure:
            raise RuntimeError('Simulated service failure')
        if argv[:3] == ['docker', 'image', 'inspect']:
            identity = next(image for image in self.images.values() if image['backupTag'] == argv[3])
            return json.dumps([{'Id': identity['id']}])
        if argv[:2] == ['docker', 'inspect']:
            service = 'ai' if argv[2].endswith('-ai-1') else 'backend'
            return json.dumps([{'Image': self.images[service]['containerImageDigest'], 'State': {'Running': True},
                                'Config': {'Labels': {'com.docker.compose.project': self.project,
                                                      'com.docker.compose.service': service}}}])
        return '{}'

    def restore(self, public_check=lambda origin: None):
        backup, record, _ = load_rollback_record(self.bundle, self.operation)
        restore_services(self.bundle, backup, record, self.compose, self.command, lambda: None, public_check)

    def test_restores_exact_files_images_before_reopening_and_keeps_data(self):
        live_data = self.root / 'simulated-database-volume'
        live_data.write_text('records added after deployment', encoding='utf-8')
        def public_check(origin):
            self.assertEqual(origin, self.release['origin'])
            self.assertTrue(all(checksum(self.bundle / name) == digest for name, digest in self.release['baseFiles'].items()))
        self.restore(public_check)
        self.assertEqual(json.loads((self.bundle / 'bundle-manifest.json').read_text()), self.original)
        self.assertEqual(live_data.read_text(), 'records added after deployment')
        names = [name for _, name in self.commands]
        self.assertLess(names.index('verify-saved-backend'), names.index('close-access-for-rollback'))
        self.assertLess(names.index('restored-ai-health'), names.index('reopen-previous-server'))
        for argv, _ in self.commands:
            self.assertFalse({'down', 'rm', 'build', 'db', 'maintenance'}.intersection(argv))
            if 'up' in argv:
                self.assertIn('--no-build', argv)
                self.assertEqual(argv[argv.index('--pull') + 1], 'never')
            if argv[:2] == ['docker', 'tag']:
                self.assertTrue(argv[2].startswith('sha256:'))
        # Retrying after an interrupted/successful restore remains bounded to this same version.
        self.restore()
        self.assertEqual((self.bundle / '.env').read_text(), 'fixture-only')

    def test_missing_image_rejects_before_stopping_services(self):
        self.failure = 'verify-saved-backend'
        with self.assertRaises(RuntimeError):
            self.restore()
        self.assertFalse(any('stop' in argv for argv, _ in self.commands))

    def test_unhealthy_backend_keeps_public_access_closed(self):
        self.failure = 'restore-backend'
        with self.assertRaises(RuntimeError):
            self.restore()
        self.assertEqual(self.commands[-1][0][-2:], ['stop', 'edge'])
        self.assertNotIn('reopen-previous-server', [name for _, name in self.commands])

    def test_failed_public_probe_closes_edge_again(self):
        def failed(origin):
            raise RuntimeError('Synthetic endpoint unavailable')
        with self.assertRaises(RuntimeError):
            self.restore(failed)
        self.assertEqual(self.commands[-1][1], 'keep-failed-rollback-private')

    def test_rejects_changed_backup_file(self):
        (self.backup / 'backend/app.jar').write_bytes(b'tampered')
        with self.assertRaisesRegex(RuntimeError, 'Saved source'):
            load_rollback_record(self.bundle, self.operation)

    def test_rejects_modified_receipt(self):
        (self.backup / 'rollback.json').write_text('{}')
        with self.assertRaisesRegex(RuntimeError, 'record checksum'):
            load_rollback_record(self.bundle, self.operation)

    def test_rejects_changed_database_schema_apk_and_private_config(self):
        for name in ('migrations/V008__capture_date_and_symptoms.sql', 'distribution/SkinCare-AI.apk', '.env'):
            with self.subTest(name=name):
                file = self.bundle / name
                original = file.read_bytes()
                file.write_bytes(b'changed-after-update')
                with self.assertRaisesRegex(RuntimeError, 'Protected'):
                    load_rollback_record(self.bundle, self.operation)
                file.write_bytes(original)

    def test_rejects_later_deployment_manifest_and_code(self):
        marker = self.bundle / 'bundle-manifest.json'
        original = marker.read_bytes()
        value = json.loads(original)
        value['unrelatedNewRelease'] = 'newer'
        marker.write_text(json.dumps(value))
        with self.assertRaisesRegex(RuntimeError, 'Another deployment'):
            load_rollback_record(self.bundle, self.operation)
        marker.write_bytes(original)
        (self.bundle / 'backend/app.jar').write_bytes(b'another-version')
        with self.assertRaisesRegex(RuntimeError, 'Another deployment'):
            load_rollback_record(self.bundle, self.operation)

    def test_rejects_pointer_path_traversal(self):
        pointer = self.operation / 'latest-rollback.json'
        data = json.loads(pointer.read_text())
        data['backupName'] = '../elsewhere'
        pointer.write_text(json.dumps(data))
        with self.assertRaisesRegex(RuntimeError, 'pointer'):
            load_rollback_record(self.bundle, self.operation)

    def test_accepts_interrupted_restore_with_only_known_old_and_new_files(self):
        shutil.copy2(self.backup / 'backend/app.jar', self.bundle / 'backend/app.jar')
        shutil.copy2(self.backup / 'bundle-manifest.json', self.bundle / 'bundle-manifest.json')
        self.restore()

    def test_saved_backup_is_independent_of_mutable_status_reports(self):
        (self.root / 'develop-upgrade.json').write_text('{"checkOnly": true}')
        _, record, _ = load_rollback_record(self.bundle, self.operation)
        self.assertEqual(record['images'], self.images)
        before = json.loads(json.dumps(self.original))
        upgraded_manifest(self.original, self.release, self.backup.name)
        self.assertEqual(self.original, before)

    def test_check_cli_does_not_stop_services_retag_images_or_overwrite_update_report(self):
        (self.root / 'develop-upgrade.json').write_text('{"passed": true}')
        before = checksum(self.bundle / 'bundle-manifest.json')
        commands = []
        def run(argv, **kwargs):
            commands.append(argv)
            if argv[:3] == ['docker', 'volume', 'inspect']:
                output = json.dumps([{'Name': self.project + '_' + name, 'Labels': {
                    'com.docker.compose.project': self.project, 'com.docker.compose.volume': name}}
                    for name in ('database', 'maintenance')])
            elif argv[:3] == ['docker', 'image', 'inspect']:
                image = next(i for i in self.images.values() if i['backupTag'] == argv[3])
                output = json.dumps([{'Id': image['id']}])
            else:
                output = 'synthetic-engine'
            return SimpleNamespace(returncode=0, stdout=output.encode())
        with patch('sys.argv', ['rollback', '--config', str(self.config), '--check']), \
                patch.object(cli, 'protect'), patch.object(cli.subprocess, 'run', side_effect=run):
            cli.main()
        self.assertTrue(all(argv[:3] in (['docker', 'version', '--format'], ['docker', 'volume', 'inspect'],
                                        ['docker', 'image', 'inspect']) for argv in commands))
        self.assertEqual(checksum(self.bundle / 'bundle-manifest.json'), before)
        self.assertEqual((self.root / 'develop-upgrade.json').read_text(), '{"passed": true}')
        self.assertTrue(json.loads((self.root / 'develop-rollback-check.json').read_text())['passed'])

    def run_update_fixture(self, fail_build=False):
        source = self.root / 'candidate'
        for name in FILES:
            target = source / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(self.bundle / name, target)
            shutil.copy2(self.backup / name, self.bundle / name)
        shutil.copy2(self.backup / 'bundle-manifest.json', self.bundle / 'bundle-manifest.json')
        release = dict(self.release, format='skincare-develop-services-v1')
        (source / 'release.json').write_text(json.dumps(release))

        def run(argv, **kwargs):
            code, output = 0, '{}'
            if argv[:3] == ['docker', 'volume', 'inspect']:
                output = json.dumps([{'Name': self.project + '_' + name, 'Labels': {
                    'com.docker.compose.project': self.project, 'com.docker.compose.volume': name}}
                    for name in ('database', 'maintenance')])
            elif argv[:3] == ['docker', 'image', 'inspect']:
                service = 'ai' if '-ai:' in argv[3] else 'backend'
                output = json.dumps([{'Id': self.images[service]['id']}])
            elif argv[:2] == ['docker', 'inspect']:
                output = self.command(argv, 'inspect-fixture')
            elif 'build' in argv:
                # The durable backup must exist before the first potentially failing build.
                backup, record, _ = load_rollback_record(self.bundle, self.operation)
                self.assertNotEqual(backup, self.backup)
                self.assertEqual(record['oldFiles'], self.release['baseFiles'])
                if fail_build:
                    code = 1
            elif 'maintenance' in argv and 'exec' in argv:
                output = '{"backupVerified": true}'
            return SimpleNamespace(returncode=code, stdout=output.encode())

        def identity(container, project, service, command, save):
            return dict(self.images[service])

        def restored(*args):
            restore_services(*args, public_check=lambda origin: None)

        with patch('sys.argv', ['update', '--config', str(self.config), '--release', str(source)]), \
                patch.object(updater, 'protect'), patch.object(updater.subprocess, 'run', side_effect=run), \
                patch.object(updater, 'resolve_restore_image', side_effect=identity), \
                patch.object(updater, 'verify_public'), patch.object(updater, 'restore_services', side_effect=restored):
            if fail_build:
                with self.assertRaises(RuntimeError):
                    updater.main()
            else:
                updater.main()
                previous = (self.root / 'develop-upgrade.json').read_bytes()
                with patch('sys.argv', ['update', '--config', str(self.config), '--release', str(source), '--check']):
                    updater.main()
                self.assertEqual((self.root / 'develop-upgrade.json').read_bytes(), previous)
        return json.loads((self.root / 'develop-upgrade.json').read_text())

    def test_update_success_persists_usable_rollback_after_later_preflight(self):
        report = self.run_update_fixture()
        self.assertTrue(report['passed'])
        _, record, original = load_rollback_record(self.bundle, self.operation)
        self.assertEqual(original, self.original)
        self.assertEqual(record['images']['ai']['id'], self.images['ai']['id'])
        self.assertEqual(json.loads((self.bundle / 'bundle-manifest.json').read_text()), record['newManifest'])

    def test_failed_update_automatically_uses_durable_rollback(self):
        report = self.run_update_fixture(fail_build=True)
        self.assertFalse(report['passed'])
        self.assertTrue(report['rollbackPassed'])
        self.assertEqual(json.loads((self.bundle / 'bundle-manifest.json').read_text()), self.original)
        self.assertTrue(all(checksum(self.bundle / name) == digest for name, digest in self.release['baseFiles'].items()))


if __name__ == '__main__':
    unittest.main()
