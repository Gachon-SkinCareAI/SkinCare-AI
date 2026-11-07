import json
from pathlib import Path
import tempfile
import unittest
from run_free_demo import checksum, compose_command
from update_progress_services import FILES, validate_release, verify_base

class ProgressReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.bundle=self.root/'bundle';self.source=self.root/'candidate'
        self.bundle.mkdir();self.source.mkdir()
        for name in FILES:
            path=self.source/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'candidate')
        old=self.bundle/'backend/app.jar';old.parent.mkdir();old.write_bytes(b'old')
        self.data={'format':'skincare-progress-services-v1','files':{n:checksum(self.source/n) for n in FILES},
                   'baseFiles':{n:checksum(self.bundle/n) if (self.bundle/n).exists() else None for n in FILES},'protectedFiles':{}}
        self.save()
    def save(self): (self.source/'release.json').write_text(json.dumps(self.data))
    def test_exact_candidate_and_unchanged_source(self):
        validate_release(self.source,self.bundle);verify_base(self.bundle,self.data)
        self.assertEqual((self.bundle/'backend/app.jar').read_bytes(),b'old')
        self.assertFalse((self.bundle/'migrations').exists())
    def test_changed_jar_refuses_mutation(self):
        (self.bundle/'backend/app.jar').write_bytes(b'other')
        with self.assertRaises(RuntimeError):verify_base(self.bundle,self.data)
    def test_unexpected_file_and_existing_migration_are_rejected(self):
        self.data['files']['private/secret']='x';self.save()
        with self.assertRaises(RuntimeError):validate_release(self.source,self.bundle)
        self.data['files'].pop('private/secret');self.save()
        path=self.bundle/'migrations/V008__capture_date_and_symptoms.sql';path.parent.mkdir();path.write_bytes(b'other')
        with self.assertRaises(RuntimeError):verify_base(self.bundle,self.data)
    def test_corrupt_candidate_and_protected_model_are_rejected(self):
        (self.source/'backend/app.jar').write_bytes(b'corrupt')
        with self.assertRaises(RuntimeError):validate_release(self.source,self.bundle)
        (self.source/'backend/app.jar').write_bytes(b'candidate')
        model=self.bundle/'model';model.write_bytes(b'baseline');self.data['protectedFiles']['model']=checksum(model);self.save();model.write_bytes(b'changed')
        with self.assertRaises(RuntimeError):validate_release(self.source,self.bundle)
    def test_download_overlay_requires_expected_hash(self):
        manifest={'deploymentMode':'temporary-tunnel','project':'skincare-free-demo-12345678','distributionEnabled':True,'sources':{}}
        with self.assertRaises(RuntimeError):compose_command(self.bundle,manifest)
        path=self.bundle/'compose.distribution.yaml';path.write_text('{}');manifest['sources'][path.name]=checksum(path)
        self.assertIn(str(path),compose_command(self.bundle,manifest))

if __name__=='__main__': unittest.main()
