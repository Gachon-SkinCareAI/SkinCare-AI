import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import diagnose_upload_startup as diagnosis
import test_upload_recovery as fixtures


class StartupDiagnosisTests(unittest.TestCase):
    def exercise(self, stop=False, cleanup_failure=False):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);bundle=root/'bundle';folder=bundle/'public-web/web-1';folder.mkdir(parents=True)
            source=fixtures.UploadRecoveryTests().fixture(root)
            project='skincare-free-demo-12345678';origin='https://fixture-test.trycloudflare.com'
            receipt={'release':'web-1','origin':origin}
            files={bundle/'bundle-manifest.json':{'project':project},bundle/'public-web-active.json':receipt,
                   folder/'release.json':{'files':{}},folder/'compose.public-web.json':{'services':{}},
                   root/'config.json':{'format':'skincare-free-demo-v1','runtime':str(root)},
                   root/'status.json':{'active':True,'passed':True,'origin':origin}}
            for path,value in files.items():path.write_text(json.dumps(value))
            originals={path:path.read_bytes() for path in files}
            if stop:(root/'stop.request').write_text('stop')
            args=SimpleNamespace(config=root/'config.json',public_status=root/'status.json',release=source,output=root/'output',check_only=False)
            calls=[];profiles=[]
            def execute(command,**kwargs):
                calls.append(command)
                if 'inspect' in command:
                    result=[{'Id':name,'Image':'sha256:'+'a'*64,'Config':{'Labels':{
                        'com.docker.compose.project':project,'com.docker.compose.service':name}}}
                        for name in ('db','llm','backend','edge','maintenance','ai')]
                else:result={'services':{'ai':{'environment':{'AI_CPU_THREADS':'2','OMP_NUM_THREADS':'2','MKL_NUM_THREADS':'2'}}}}
                return subprocess.CompletedProcess(command,0,json.dumps(result).encode(),b'')
            def check(settings,source,image,operation,project,bundle,out,execute,current,step,probe,validation_photo=None):
                profiles.append(settings['services']['ai']['environment'].copy())
                step('check-isolated-ai-startup')
                if not cleanup_failure:(out/'remove-isolated-ai.log').write_text('removed')
                if len(profiles)==1:
                    raise diagnosis.IsolatedCheckError('test failure','remove-isolated-ai' if cleanup_failure else 'check-isolated-ai-startup')
                return True
            with patch.object(diagnosis,'validate_active',return_value=receipt),patch.object(diagnosis,'verify_snapshot'),patch.object(diagnosis,'compose_command',return_value=['docker','compose']),patch.object(diagnosis,'check_isolated',side_effect=check):
                if stop or cleanup_failure:
                    with self.assertRaises(RuntimeError):diagnosis.diagnose(args,execute)
                else:
                    report=diagnosis.diagnose(args,execute)
                    self.assertTrue(report['completed'])
                    self.assertFalse(report['cases']['existing-threads']['passed'])
                    self.assertTrue(report['cases']['single-thread']['passed'])
                    self.assertTrue(report['productionIdentitiesUnchanged'])
            for path,value in originals.items():self.assertEqual(path.read_bytes(),value)
            self.assertFalse(any('up' in c or 'down' in c for c in calls))
            if stop:self.assertEqual(calls,[])
            elif cleanup_failure:self.assertEqual(len(profiles),1)
            else:
                self.assertEqual(profiles[0]['AI_CPU_THREADS'],'2')
                self.assertEqual(profiles[1],diagnosis.SINGLE_THREAD)

    def test_compares_both_profiles_without_deploying_or_altering_active_files(self):self.exercise()
    def test_public_stop_cancels_before_creating_containers(self):self.exercise(stop=True)
    def test_cleanup_failure_prevents_next_container(self):self.exercise(cleanup_failure=True)


if __name__=='__main__':unittest.main()
