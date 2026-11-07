import http.client
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from public_web import BASE_FILES, FORMAT, http_get, public_overlay, validate_active, prepare_public_models
from run_free_demo import checksum, compose_command


class PublicWebTests(unittest.TestCase):
    def run_model_preparation(self, warmup=True, seconds=1.2, mmap=True):
        calls = []
        def run(command, name, timeout):
            calls.append((command, name))
            if name == 'check-public-vision-warmup':
                return json.dumps({'event':'vision_warmup','synthetic_input_only':True,
                    'device':'cpu','detector_ms':100,'classifier_ms':200,'total_ms':310}) if warmup else ''
            if name == 'check-public-vision-http-speed':
                return json.dumps({'passed':True,'checks':[
                    {'status':200,'seconds':seconds,'qualityPassed':True},
                    {'status':200,'seconds':1.1,'qualityPassed':True}]})
            return ''
        with tempfile.TemporaryDirectory() as directory, patch('public_web.prepare_local_model', return_value={'passed':True,'modelOptions':{'use_mmap':mmap}}) as qa:
            result = prepare_public_models(['docker','compose'], {'localLlmEnabled':True}, run, directory)
            qa.assert_called_once()
            self.assertTrue((Path(directory)/'public-model-readiness.json').is_file())
        return result, calls

    def test_public_start_checks_full_warmup_and_live_speed(self):
        result, calls = self.run_model_preparation()
        self.assertTrue(result['passed'])
        self.assertIn('--force-recreate', calls[0][0])
        self.assertEqual(calls[0][0][-1], 'ai')
        self.assertEqual([name for _,name in calls], ['prepare-public-vision-process',
                         'check-public-vision-warmup','check-public-vision-http-speed'])

    def test_healthy_status_without_full_classifier_warmup_is_insufficient(self):
        with self.assertRaisesRegex(RuntimeError, 'detector/classifier warmup'):
            self.run_model_preparation(warmup=False)

    def test_slow_live_inference_cannot_publish_ready_state(self):
        with self.assertRaisesRegex(RuntimeError, 'first-upload budget'):
            self.run_model_preparation(seconds=89)

    def test_legacy_runtime_cannot_publish_with_mismatched_loading_options(self):
        with self.assertRaisesRegex(RuntimeError, 'reviewed loading options'):
            self.run_model_preparation(mmap=False)

    @unittest.skipUnless(os.name == 'nt', 'Windows PowerShell compatibility test')
    def test_windows_powershell_resolves_default_mobile_path(self):
        # Exercise -File parameter binding, including paths with spaces. The fake
        # exporter records the selected project without starting Metro or Docker.
        with tempfile.TemporaryDirectory(prefix='public web ps5 ') as directory:
            root = Path(directory)
            scripts = root / 'deploy/public-demo'
            scripts.mkdir(parents=True)
            source = Path(__file__).resolve().parent
            for name in ('Build-Public-Web.ps1', 'native_command.ps1'):
                shutil.copy2(source / name, scripts / name)
            mobile = root / 'mobile'
            executable = mobile / 'node_modules/.bin/expo.cmd'
            executable.parent.mkdir(parents=True)
            executable.write_bytes(b'@echo off\r\n> "%~dp0..\\..\\called.txt" echo %EXPO_PUBLIC_API_URL%\r\nexit /b 0\r\n')
            origin = 'https://powershell-fixture.example'
            result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                '-File', str(scripts / 'Build-Public-Web.ps1'), '-ApiOrigin', origin,
                '-OutputDirectory', str(root / 'web')], capture_output=True, timeout=45)
            self.assertEqual(result.returncode, 0, result.stderr.decode(errors='replace'))
            self.assertEqual((mobile / 'called.txt').read_text().strip(), origin)

    def test_untrusted_or_non_origin_addresses_rejected(self):
        for origin in ('http://test.trycloudflare.com', 'https://localhost',
                       'https://test.trycloudflare.com/path', 'https://test.trycloudflare.com:444',
                       'https://user@test.trycloudflare.com', 'https://test.trycloudflare.com?x=1',
                       'https://test.trycloudflare.com.evil.example'):
            with self.subTest(origin=origin), self.assertRaises(RuntimeError):
                public_overlay(Path('release'), origin)

    def test_overlay_preserves_data_services_and_only_mounts_ai_client_readonly(self):
        overlay = public_overlay(Path('release'), 'https://test-public.trycloudflare.com')
        self.assertEqual(set(overlay), {'services'})
        self.assertEqual(set(overlay['services']), {'backend', 'edge', 'ai'})
        for service in overlay['services'].values():
            self.assertTrue(set(service).issubset({'environment', 'volumes'}))
        mounts = overlay['services']['edge']['volumes']
        self.assertEqual({m['target'] for m in mounts}, {'/etc/caddy/Caddyfile', '/srv/public-web'})
        self.assertTrue(all(m['read_only'] and not m['bind']['create_host_path'] for m in mounts))
        ai = overlay['services']['ai']
        self.assertEqual(set(ai), {'volumes'})
        self.assertEqual(len(ai['volumes']), 1)
        mount = ai['volumes'][0]
        self.assertEqual(mount['target'], '/app/qa/llm_client.py')
        self.assertEqual(mount['source'], str((Path('release')/'llm_client.py').resolve()))
        self.assertTrue(mount['read_only'])
        self.assertFalse(mount['bind']['create_host_path'])

    def test_other_launchers_refuse_active_public_session(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            (bundle / 'public-web-active.json').write_text('{}')
            manifest = {'project': 'skincare-free-demo-12345678', 'deploymentMode': 'temporary-tunnel'}
            with self.assertRaisesRegex(RuntimeError, 'STOP-PUBLIC-PHONE'):
                compose_command(bundle, manifest)
            self.assertIn('compose', compose_command(bundle, manifest, allow_public_web=True))

    def test_recovery_rejects_changed_base_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            manifest = {'project': 'skincare-free-demo-12345678'}
            for name in BASE_FILES:
                (bundle / name).write_text('{}')
            receipt = {'format': FORMAT, 'project': manifest['project'], 'release': 'web-123',
                       'baseFiles': {name: checksum(bundle / name) for name in BASE_FILES}}
            (bundle / 'public-web-active.json').write_text(json.dumps(receipt))
            with patch('public_web.active_overlay') as check:
                self.assertEqual(validate_active(bundle, manifest), receipt)
                check.assert_called_once_with(bundle, manifest)
                (bundle / '.env').write_text('unexpected-change')
                with self.assertRaisesRegex(RuntimeError, 'Base server changed'):
                    validate_active(bundle, manifest)

    def test_disconnected_http_is_a_retryable_readiness_failure(self):
        with patch('urllib.request.urlopen', side_effect=http.client.RemoteDisconnected('fixture')):
            with self.assertRaises(OSError):
                http_get('https://test.trycloudflare.com', '/')


if __name__ == '__main__':
    unittest.main()
