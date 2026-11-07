import json
from pathlib import Path
import tempfile
import unittest

from run_free_demo import checksum
from startup_stability_release import FILES, active_overlay, overlay_config, validate_release


class StartupReleaseTests(unittest.TestCase):
    def test_overlay_only_mounts_allowlisted_sources_read_only(self):
        config = overlay_config(Path('fixture'))
        self.assertEqual(set(config['services']), {'ai', 'maintenance', 'edge'})
        mounts = [v for s in config['services'].values() for v in s['volumes']]
        self.assertEqual(len(mounts), len(FILES))
        self.assertTrue(all(v['read_only'] and not v['bind']['create_host_path'] for v in mounts))
        self.assertFalse(any(v['target'] in ('/state', '/models', '/var/lib/mysql') for v in mounts))
        self.assertTrue(all('ports' not in s and 'networks' not in s for s in config['services'].values()))

    def test_changed_sources_or_base_manifest_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            manifest = {'project': 'skincare-free-demo-12345678'}
            (bundle / 'bundle-manifest.json').write_text(json.dumps(manifest))
            folder = bundle / 'startup-stability/startup-20261107130000'
            folder.mkdir(parents=True)
            files = {}
            for name in FILES:
                path = folder / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('fixture')
                files[name] = checksum(path)
            release = {'format': 'skincare-startup-stability-v1', 'project': manifest['project'],
                       'id': folder.name, 'files': files}
            (folder / 'release.json').write_text(json.dumps(release))
            overlay = folder / 'compose.startup-stability.json'
            overlay.write_text(json.dumps(overlay_config(folder)))
            receipt = {'format': 'skincare-startup-stability-active-v1', 'project': manifest['project'],
                'release': folder.name, 'baseManifestSha256': checksum(bundle / 'bundle-manifest.json'),
                'releaseSha256': checksum(folder / 'release.json'), 'overlaySha256': checksum(overlay)}
            (bundle / 'startup-stability.json').write_text(json.dumps(receipt))
            self.assertEqual(active_overlay(bundle, manifest), overlay)
            (folder / 'maintenance.py').write_text('tampered')
            with self.assertRaises(RuntimeError):validate_release(folder)
            (folder / 'maintenance.py').write_text('fixture')
            (bundle / 'bundle-manifest.json').write_text('{}')
            with self.assertRaises(RuntimeError):active_overlay(bundle, manifest)


if __name__ == '__main__':unittest.main()
