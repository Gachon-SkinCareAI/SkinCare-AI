import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from init_vision_cache import populate,FILES
from run_free_demo import checksum,compose_command
from update_analysis_speed import validate_release,image_reference,same_image_content,resolve_restore_image,FILES as RELEASE_FILES


class VisionCacheTests(unittest.TestCase):
    def test_containerd_compose_image_digest_is_used_instead_of_config_digest(self):
        config_digest='sha256:'+'a'*64;image_digest='sha256:'+'b'*64
        container={'Image':config_digest,'Config':{'Labels':{'com.docker.compose.image':image_digest}}}
        self.assertEqual(image_reference(container),image_digest)
        self.assertEqual(image_reference({'Image':config_digest,'Config':{}}),config_digest)
        self.assertEqual(image_reference({'Image':config_digest,'Config':{},'ImageManifestDescriptor':{'digest':image_digest}}),image_digest)

    def test_mutable_or_malformed_image_references_are_rejected(self):
        with self.assertRaises(RuntimeError):image_reference({'Image':'example:latest','Config':{}})
        with self.assertRaises(RuntimeError):image_reference({'Image':'sha256:not-a-digest','Config':{}})

    def test_manifest_identity_survives_index_rebuild_but_different_content_is_rejected(self):
        running={'Image':'old-config','Config':{'Labels':{'com.docker.compose.image':'manifest-a'}}}
        candidate={'Image':'new-index','ImageManifestDescriptor':{'digest':'manifest-a'}}
        self.assertEqual(same_image_content(running,candidate,'new-index'),'compose-recorded-image-digest')
        candidate['ImageManifestDescriptor']['digest']='different-manifest'
        with self.assertRaises(RuntimeError):same_image_content(running,candidate,'new-index')

    def test_image_probe_is_never_started_and_only_its_own_container_is_removed(self):
        project='skincare-free-demo-12345678';image='sha256:'+'b'*64;probe='c'*64;calls=[]
        running={'Image':'sha256:'+'a'*64,'Config':{'Image':project+'-ai','Labels':{}}}
        def command(args,name,*unused,**kwargs):
            calls.append(args)
            if args[1:3]==['image','inspect']:return json.dumps([{'Id':image}])
            if args[1]=='create':return probe
            if args[1]=='inspect':return json.dumps([{'Image':running['Image'],'State':{'Running':False},
                'Config':{'Labels':{'skincare.speed-image-check':project}}}])
            return ''
        result=resolve_restore_image(running,project,'ai',command,lambda identity:None)
        self.assertEqual(result['verifiedBy'],'container-image-digest')
        self.assertFalse(any('start' in call for call in calls))
        self.assertIn(['docker','rm',probe],calls)
        self.assertTrue(all('--mount' not in call and '--volume' not in call for call in calls))

    def test_image_probe_cleanup_runs_when_content_does_not_match(self):
        project='skincare-free-demo-12345678';probe='c'*64;calls=[]
        running={'Image':'old','Config':{'Image':project+'-ai','Labels':{}}}
        def command(args,name,*unused,**kwargs):
            calls.append(args)
            if args[1:3]==['image','inspect']:return json.dumps([{'Id':'sha256:'+'b'*64}])
            if args[1]=='create':return probe
            if args[1]=='inspect':return json.dumps([{'Image':'different','State':{'Running':False},
                'Config':{'Labels':{'skincare.speed-image-check':project}}}])
            return ''
        with self.assertRaises(RuntimeError):resolve_restore_image(running,project,'ai',command,lambda identity:None)
        self.assertEqual(calls[-1],['docker','rm',probe])

    def fixture(self,root):
        source=root/'source';cache=root/'cache';source.mkdir();cache.mkdir()
        metadata={'files':{}}
        for name in FILES[:-1]:
            data=('original '+name).encode();(source/name).write_bytes(data)
            metadata['files'][name]={'sha256':hashlib.sha256(data).hexdigest()}
        (source/'manifest.json').write_text(json.dumps(metadata))
        return source,cache

    def test_cached_baseline_is_byte_identical_and_reusable(self):
        with tempfile.TemporaryDirectory() as directory:
            source,cache=self.fixture(Path(directory));before={p.name:p.read_bytes() for p in source.iterdir()}
            self.assertTrue(populate(source,cache)['verified'])
            self.assertTrue(populate(source,cache)['verified'])
            self.assertEqual({p.name:p.read_bytes() for p in source.iterdir()},before)
            self.assertEqual({p.name:p.read_bytes() for p in cache.iterdir()},before)

    def test_changed_source_or_existing_cache_is_preserved_and_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            source,cache=self.fixture(Path(directory))
            (cache/'classifier.pt').write_bytes(b'keep this file')
            with self.assertRaises(RuntimeError):populate(source,cache)
            self.assertEqual((cache/'classifier.pt').read_bytes(),b'keep this file')
            self.assertFalse((cache/'detector.pt').exists())
            (source/'detector.pt').write_bytes(b'changed source')
            with self.assertRaises(RuntimeError):populate(source,cache)

    def test_overlay_requires_explicit_opt_in_and_matching_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);manifest={'deploymentMode':'temporary-tunnel','project':'skincare-free-demo-12345678','sources':{}}
            self.assertNotIn(str(root/'compose.vision-cache.yaml'),compose_command(root,manifest))
            manifest['visionModelCacheEnabled']=True
            with self.assertRaises(RuntimeError):compose_command(root,manifest)
            overlay=root/'compose.vision-cache.yaml';overlay.write_text('fixture')
            manifest['sources'][overlay.name]=checksum(overlay)
            self.assertEqual(compose_command(root,manifest)[-1],str(overlay))
            overlay.write_text('tampered')
            with self.assertRaises(RuntimeError):compose_command(root,manifest)

    def test_release_tampering_or_extra_files_fail_before_service_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);hashes={}
            for name in RELEASE_FILES:
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'fixture');hashes[name]=checksum(path)
            release={'format':'skincare-analysis-speed-v1','files':hashes}
            (root/'release.json').write_text(json.dumps(release));validate_release(root)
            (root/'serve_ai.py').write_text('changed')
            with self.assertRaises(RuntimeError):validate_release(root)
            release['files']['../outside']='x';(root/'release.json').write_text(json.dumps(release))
            with self.assertRaises(RuntimeError):validate_release(root)


if __name__=='__main__':unittest.main()
