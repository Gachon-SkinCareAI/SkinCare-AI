import copy
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import apply_upload_recovery as installer
from upload_recovery_release import FILES, ENVIRONMENT, package, validate, extend, validate_compose

class UploadRecoveryTests(unittest.TestCase):
    def fixture(self, root):
        source = root/'source'
        for name in FILES:
            path = source/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('# fixture\n')
        package(source, root/'candidate')
        return root/'candidate'

    def test_tampered_source_and_unknown_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = self.fixture(Path(tmp))
            (folder/next(iter(FILES))).write_text('unexpected')
            with self.assertRaises(RuntimeError):validate(folder)

    def test_previous_file_set_requires_explicit_previous_release_validation(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=self.fixture(Path(tmp))
            release=json.loads((folder/'release.json').read_text())
            release['files'].pop('ai/qa/orchestrator.py')
            (folder/'release.json').write_text(json.dumps(release))
            with self.assertRaises(RuntimeError):validate(folder)
            self.assertEqual(validate(folder,allow_previous=True),release)
            release['files'].pop('ai/eval_classify.py')
            (folder/'release.json').write_text(json.dumps(release))
            with self.assertRaises(RuntimeError):validate(folder,allow_previous=True)

    def test_previous_active_overlay_keeps_only_its_verified_mounts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);folder=self.fixture(root)
            release=json.loads((folder/'release.json').read_text())
            release['files'].pop('ai/qa/orchestrator.py')
            (folder/'release.json').write_text(json.dumps(release))
            folder.rename(root/'upload-recovery')
            overlay=extend({'services':{'ai':{}}},root)
            mounts=overlay['services']['ai']['volumes']
            self.assertNotIn('/app/qa/orchestrator.py',{m['target'] for m in mounts})
            self.assertEqual(len(mounts),5)

    def test_new_overlay_is_readonly_and_does_not_add_public_ports(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root).rename(root/'upload-recovery')
            overlay = extend({'services': {'ai': {}}}, root)
            self.assertEqual(set(overlay['services']), {'ai','maintenance'})
            self.assertEqual(overlay['services']['ai']['environment'], ENVIRONMENT)
            for service in overlay['services'].values():
                self.assertTrue(all(m['read_only'] for m in service['volumes']))
                self.assertNotIn('ports', service)

    def test_compose_validator_rejects_port_credential_or_supervisor_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root).rename(root/'upload-recovery')
            before = {'services': {'ai': {'restart':'unless-stopped','environment':{'SECRET_REF':'unchanged'}},
                'maintenance':{},'db':{'image':'same'}}}
            after = extend(copy.deepcopy(before), root)
            validate_compose(before, after)
            for name, key, value in [('ai','restart','no'),('ai','ports',['8000:8000']),('db','image','different')]:
                wrong = copy.deepcopy(after);wrong['services'][name][key] = value
                with self.assertRaises(RuntimeError):validate_compose(before, wrong)
            wrong = copy.deepcopy(after);wrong['services']['ai']['environment']['SECRET_REF']='changed'
            with self.assertRaises(RuntimeError):validate_compose(before, wrong)

    def installation(self, fail=False, stopping=False, isolated_fail=False, active=False, copy_failure=False, photo=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp);bundle=root/'bundle';folder=bundle/'public-web/web-1'
            folder.mkdir(parents=True)
            candidate=self.fixture(root)
            old_files={}
            if active:
                old=folder/'upload-recovery';shutil.copytree(candidate,old)
                release=json.loads((old/'release.json').read_text())
                removed='ai/qa/orchestrator.py'
                (old/removed).unlink();release['files'].pop(removed)
                (old/'ai/app/vision_worker.py').write_text('# prior active version\n')
                release['files']['ai/app/vision_worker.py']=installer.checksum(old/'ai/app/vision_worker.py')
                (old/'release.json').write_text(json.dumps(release))
                old_files={p.relative_to(old).as_posix():p.read_bytes() for p in old.rglob('*') if p.is_file()}
            origin='https://fixture-test.trycloudflare.com'
            receipt={'release':'web-1','origin':origin}
            for name,value in [('bundle-manifest.json',{'project':'skincare-free-demo-12345678'}),('public-web-active.json',receipt)]:
                (bundle/name).write_text(json.dumps(value))
            original_compose={'services': {'ai': {'restart':'unless-stopped','environment':{}},'maintenance':{},'db':{},'llm':{},'backend':{},'edge':{}}}
            (folder/'compose.public-web.json').write_text(json.dumps(original_compose))
            (folder/'release.json').write_text(json.dumps({'files':{}}))
            before={name:(folder/name).read_bytes() for name in ('compose.public-web.json','release.json')}
            (root/'config.json').write_text(json.dumps({'format':'skincare-free-demo-v1','runtime':str(root)}))
            (root/'status.json').write_text(json.dumps({'active':True,'passed':True,'origin':origin}))
            if stopping:(root/'public-web-stop.request').write_text('stop')
            args=SimpleNamespace(config=root/'config.json',public_status=root/'status.json',release=candidate,output=root/'output',check_only=False)
            if photo:
                args.validation_photo=root/'validation.jpg';args.validation_photo.write_bytes(b'\xff\xd8fixture')
            calls=[];configs=0;streamed=[]
            def execute(command,**kwargs):
                nonlocal configs
                calls.append(command)
                if 'input' in kwargs:
                    self.assertIn('--stdin-photo',command)
                    self.assertIn('--interactive',command)
                    streamed.append(kwargs['input'])
                if 'cp' in command:raise AssertionError('Read-only AI cannot receive docker cp')
                code=0
                if 'inspect' in command:
                    value=[{'Id':s,'Image':'sha256:'+'a'*64,'Config':{'Labels':{
                        'com.docker.compose.service':s,'com.docker.compose.project':'skincare-free-demo-12345678'}}}
                        for s in ('db','llm','backend','edge','ai','maintenance')
                        if 'skincare-free-demo-12345678-'+s+'-1' in command]
                elif 'config' in command:
                    configs+=1
                    value=original_compose if configs==1 else extend(copy.deepcopy(original_compose),folder)
                elif 'exec' in command:
                    value={'passed':True,'validationPhoto':True};code=1 if fail else 0
                else:value={}
                return subprocess.CompletedProcess(command,code,json.dumps(value).encode(),b'')
            def overlay(folder,origin):return extend(copy.deepcopy(original_compose),folder)
            def get(origin,path):return ({'/api/wc/analyses':401,'/api/v1/ai/health':404}.get(path,200),b'{}')
            copytree=shutil.copytree
            def copy_source(src,dst,*a,**kw):
                if copy_failure:
                    Path(dst).mkdir();(Path(dst)/'partial').write_text('partial candidate')
                    raise OSError('simulated copy interruption')
                return copytree(src,dst,*a,**kw)
            with patch.object(installer,'HERE',Path(__file__).resolve().parent),patch.object(installer.shutil,'copytree',side_effect=copy_source),patch.object(installer,'check_isolated',side_effect=RuntimeError('Isolated startup failed') if isolated_fail else None,return_value=True),patch.object(installer,'validate_active',return_value=receipt),patch.object(installer,'verify_snapshot',return_value={'files':{}}),patch.object(installer,'load_rollback_record'),patch.object(installer,'compose_command',return_value=['docker','compose']),patch.object(installer,'public_overlay',side_effect=overlay),patch.object(installer.time,'sleep'):
                if fail or stopping or isolated_fail or copy_failure:
                    with self.assertRaises((RuntimeError,OSError)):installer.apply(args,execute,get)
                    for name, data in before.items():self.assertEqual((folder/name).read_bytes(), data)
                    if active:
                        restored={p.relative_to(folder/'upload-recovery').as_posix():p.read_bytes() for p in (folder/'upload-recovery').rglob('*') if p.is_file()}
                        self.assertEqual(restored,old_files)
                    else:self.assertFalse((folder/'upload-recovery').exists())
                    if fail or copy_failure:
                        self.assertTrue(json.loads((root/'output/recovery-status.json').read_text())['priorRuntimeRestored'])
                        self.assertTrue((root/'output/failed-production-ai.json').is_file())
                        logs_index = next(i for i,c in enumerate(calls) if 'logs' in c)
                        restore_index = next(i for i,c in enumerate(calls) if c[-2:] == ['maintenance','ai'])
                        self.assertLess(logs_index, restore_index)
                    if isolated_fail:
                        self.assertFalse(any('up' in c for c in calls))
                else:
                    result=installer.apply(args,execute,get)
                    self.assertTrue(result['passed'])
                    self.assertTrue((folder/'upload-recovery').is_dir())
                    self.assertEqual(validate(folder/'upload-recovery'),validate(candidate))
                    if active:
                        saved=Path(result['backup'])/'upload-recovery'
                        self.assertEqual({p.relative_to(saved).as_posix():p.read_bytes() for p in saved.rglob('*') if p.is_file()},old_files)
                    if photo:
                        self.assertTrue(result['liveStoredValidationPhotoVerified'])
                        self.assertEqual(sum('--stdin-photo' in c for c in calls),2)
                        self.assertEqual(streamed,[args.validation_photo.read_bytes()]*2)
                        self.assertEqual(sum('cp' in c for c in calls),0)
                    changed=[c[-1] for c in calls if 'up' in c]
                    self.assertEqual(changed,['maintenance','ai'])
    def test_success_recreates_only_ai_and_maintenance(self):self.installation()
    def test_failed_live_probe_restores_previous_release(self):self.installation(fail=True)
    def test_stop_request_prevents_runtime_mutation(self):self.installation(stopping=True)
    def test_failed_isolated_check_leaves_production_untouched(self):self.installation(isolated_fail=True)
    def test_active_previous_release_can_be_upgraded_and_is_backed_up(self):self.installation(active=True,photo=True)
    def test_failed_upgrade_restores_active_package_byte_for_byte(self):self.installation(active=True,fail=True)
    def test_interrupted_package_copy_restores_active_package(self):self.installation(active=True,copy_failure=True)
    def test_failed_isolated_upgrade_keeps_active_package(self):self.installation(active=True,isolated_fail=True)

if __name__=='__main__':unittest.main()
