import json
from pathlib import Path
import tempfile
import unittest
from update_guest_demo import FILES, checksum, validate_release, verify_base

class GuestReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name); self.source=self.root/'source'; self.bundle=self.root/'bundle'
        self.source.mkdir(); self.bundle.mkdir()
        for name in FILES:
            p=self.source/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'candidate')
        old=self.bundle/'backend/app.jar'; old.parent.mkdir(); old.write_bytes(b'old')
        self.data={'format':'skincare-guest-public-v1',
            'files':{n:checksum(self.source/n) for n in FILES},
            'baseFiles':{n:checksum(self.bundle/n) if (self.bundle/n).exists() else None for n in FILES},
            'protectedFiles':{}}
        self.save()
    def save(self):
        (self.source/'release.json').write_text(json.dumps(self.data))
    def test_valid_candidate_does_not_modify_original(self):
        validate_release(self.source); verify_base(self.bundle,self.data)
        self.assertEqual((self.bundle/'backend/app.jar').read_bytes(),b'old')
        self.assertFalse((self.bundle/'migrations').exists())
    def test_corrupt_candidate_is_rejected(self):
        (self.source/'backend/app.jar').write_bytes(b'corrupt')
        with self.assertRaises(RuntimeError):validate_release(self.source)
    def test_unlisted_model_or_private_file_is_rejected(self):
        for name in ('models/classifier.pt','private/db-root.txt','../outside'):
            self.data['files'][name]='x'; self.save()
            with self.assertRaises(RuntimeError):validate_release(self.source)
            self.data['files'].pop(name)
    def test_changed_deployment_is_rejected(self):
        (self.bundle/'backend/app.jar').write_bytes(b'other-change')
        with self.assertRaises(RuntimeError):verify_base(self.bundle,self.data)
    def test_existing_new_migration_is_never_overwritten(self):
        name=next(n for n in FILES if n.startswith('migrations/'))
        p=self.bundle/name;p.parent.mkdir();p.write_bytes(b'other-migration')
        with self.assertRaises(RuntimeError):verify_base(self.bundle,self.data)
    def test_protected_file_mismatch_is_rejected(self):
        p=self.bundle/'model';p.write_bytes(b'original')
        self.data['protectedFiles']['model']=checksum(p);p.write_bytes(b'changed')
        with self.assertRaises(RuntimeError):verify_base(self.bundle,self.data)

if __name__=='__main__':unittest.main()
