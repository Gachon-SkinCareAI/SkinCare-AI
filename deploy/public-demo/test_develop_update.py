import json
from pathlib import Path
import tempfile
import unittest
from update_develop_services import FILES, bounded, checksum, validate_release


class DevelopReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source, self.bundle = self.root / 'release', self.root / 'bundle'
        self.data = {'format': 'skincare-develop-services-v1', 'files': {}, 'baseFiles': {}, 'protectedFiles': {}}
        for name in FILES:
            for root, content in ((self.source, b'candidate'), (self.bundle, b'existing')):
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(content)
            self.data['files'][name] = checksum(self.source / name)
            self.data['baseFiles'][name] = checksum(self.bundle / name)
        for name in ('compose.yaml', 'migrations/V008__capture_date_and_symptoms.sql'):
            p = self.bundle / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b'protected')
            self.data['protectedFiles'][name] = checksum(p)
        self.save()

    def save(self):
        (self.source / 'release.json').write_text(json.dumps(self.data), encoding='utf-8')

    def test_valid_release_and_applied_release_have_distinct_baselines(self):
        validate_release(self.source, self.bundle)
        with self.assertRaises(RuntimeError):
            validate_release(self.source, self.bundle, applied=True)
        for name in FILES:
            (self.bundle / name).write_bytes((self.source / name).read_bytes())
        validate_release(self.source, self.bundle, applied=True)
        with self.assertRaises(RuntimeError):
            validate_release(self.source, self.bundle)

    def test_candidate_tampering_is_rejected(self):
        (self.source / 'backend/app.jar').write_bytes(b'tampered')
        with self.assertRaises(RuntimeError):
            validate_release(self.source, self.bundle)

    def test_existing_config_change_is_rejected(self):
        (self.bundle / 'compose.yaml').write_bytes(b'changed')
        with self.assertRaises(RuntimeError):
            validate_release(self.source, self.bundle)

    def test_extra_mutation_and_traversal_are_rejected(self):
        self.data['files']['private/key'] = 'unexpected'
        self.save()
        with self.assertRaises(RuntimeError):
            validate_release(self.source, self.bundle)
        with self.assertRaises(RuntimeError):
            bounded(self.bundle, '../secret')


if __name__ == '__main__':
    unittest.main()
