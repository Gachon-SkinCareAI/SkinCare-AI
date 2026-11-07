import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import upload_recovery_check as check
import test_upload_recovery as fixtures


class IsolatedCheckTests(unittest.TestCase):
    def baseline(self):
        return {'services': {'ai': {'image':'previous-tag', 'restart':'unless-stopped',
            'networks':{'service':None}, 'depends_on':{'llm':{}}, 'ports':[{'target':8000}],
            'volumes':[{'type':'volume','source':'models','target':'/models','read_only':True}],
            'environment':{'AI_DETECT_PATH':'/models/detector.pt'},
            'secrets':[{'source':'ai_token'}], 'read_only':True}},
            'volumes':{'models':{'name':'production_models'}},
            'secrets':{'ai_token':{'file':'private/ai-token.txt'},'db_root':{'file':'private/db.txt'}}}

    def test_isolation_preserves_model_readonly_and_excludes_other_credentials(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=fixtures.UploadRecoveryTests().fixture(Path(tmp))
            config=check.isolated_config(self.baseline(),source,'sha256:'+'a'*64)
            ai=config['services']['ai']
            self.assertEqual(ai['network_mode'],'none')
            self.assertEqual(ai['restart'],'no')
            self.assertNotIn('ports',ai)
            self.assertNotIn('networks',ai)
            self.assertNotIn('depends_on',ai)
            self.assertEqual(config['volumes'],{'models':{'name':'production_models','external':True}})
            self.assertEqual(set(config['secrets']),{'ai_token'})
            broken=self.baseline();broken['services']['ai']['volumes'][0]['read_only']=False
            with self.assertRaises(RuntimeError):check.isolated_config(broken,source,'sha256:'+'a'*64)

    def test_diagnostics_include_stage_and_stack_without_private_message(self):
        raw='''{"event":"vision_prepare_stage","stage":"warmup","token":"secret-value"}
Timeout (0:02:00)!
  File "/app/vision/pipeline.py", line 84, in warmup
RuntimeError: private-patient-text
Authorization: Bearer secret-value
'''
        report=check.summarize_logs(raw)
        self.assertTrue(report['deadlineExpired'])
        self.assertEqual(report['events'],[{'event':'vision_prepare_stage','stage':'warmup'}])
        self.assertEqual(report['stackFrames'][0]['function'],'warmup')
        self.assertNotIn('secret-value',json.dumps(report))
        self.assertNotIn('private-patient-text',json.dumps(report))

    def test_compose_prefix_does_not_discard_timeout_stack_frames(self):
        raw = '''ai-1  | Timeout (0:02:00)!
ai-1  | Thread 0x00001 (most recent call first):
ai-1  |   File "/app/vision/pipeline.py", line 55 in configure_cpu
ai-1  |     private_source_line_not_to_collect()
ai-1  |   File "/usr/local/lib/python3.10/concurrent/futures/thread.py", line 58, in run
'''
        report = check.summarize_logs(raw)
        self.assertEqual([f['function'] for f in report['stackFrames']],['configure_cpu','run'])
        self.assertTrue(report['deadlineExpired'])
        self.assertNotIn('private_source_line',json.dumps(report))

    def exercise(self, failure=None, real_photo=False, questionnaire=False):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=fixtures.UploadRecoveryTests().fixture(root)
            photo=root/'public-validation.jpg'
            if real_photo:photo.write_bytes(b'\xff\xd8fixture')
            calls=[];streamed=[]
            def execute(command,**kwargs):
                calls.append(command)
                if 'input' in kwargs:
                    self.assertIn('--stdin-photo',command)
                    self.assertIn('--interactive',command)
                    streamed.append(kwargs['input'])
                if 'up' in command and failure=='timeout':
                    raise subprocess.TimeoutExpired(command,210)
                code=1 if ('up' in command and failure=='startup') or ('exec' in command and failure=='photo') or ('--stdin-photo' in command and failure=='real-photo') or (command[-1]=='question-probe' and failure=='question') else 0
                if 'exec' in command:value=json.dumps({'passed':True,'validationPhoto':True}).encode()
                elif 'logs' in command:value=b'Timeout (0:02:00)!\n'
                elif 'ps' in command:value=b'{"State":"exited","Health":"","ExitCode":1}'
                else:value=b''
                return subprocess.CompletedProcess(command,code,value,b'')
            with patch.object(check.time,'sleep'):
                args=(self.baseline(),source,'sha256:'+'a'*64,root,'skincare-free-demo-12345678',root,root,execute,lambda:None,lambda _:None,'probe')
                if failure:
                    with self.assertRaises((RuntimeError,subprocess.TimeoutExpired)):check.check_isolated(*args,validation_photo=photo if real_photo else None,question_probe='question-probe' if questionnaire else None)
                else:self.assertTrue(check.check_isolated(*args,validation_photo=photo if real_photo else None,question_probe='question-probe' if questionnaire else None))
            self.assertEqual(sum('down' in c for c in calls),1)
            self.assertTrue(all(c[3].startswith('skincare-free-demo-12345678-upload-check-') for c in calls))
            self.assertLess(next(i for i,c in enumerate(calls) if 'logs' in c),next(i for i,c in enumerate(calls) if 'down' in c))
            self.assertTrue((root/'isolated-ai.json').is_file())
            self.assertIn('--no-log-prefix',next(c for c in calls if 'logs' in c))
            if not failure:self.assertEqual(sum('exec' in c for c in calls),(3 if real_photo else 2)+int(questionnaire))
            if real_photo:
                config=json.loads(next(root.glob('skincare-*/compose.json')).read_text())
                self.assertTrue(config['services']['ai']['read_only'])
                self.assertFalse(any(m.get('source')==str(photo) for m in config['services']['ai']['volumes']))
                expected=1 if failure else 2
                self.assertEqual(sum('--stdin-photo' in c for c in calls),expected)
                self.assertEqual(streamed,[photo.read_bytes()]*expected)
                self.assertFalse(any('cp' in c for c in calls))

    def test_success_checks_before_and_after_idle_and_cleans_up(self):self.exercise()
    def test_startup_failure_collects_before_cleanup(self):self.exercise('startup')
    def test_startup_timeout_still_collects_and_cleans_up(self):self.exercise('timeout')
    def test_photo_failure_cleans_up(self):self.exercise('photo')
    def test_real_photo_uses_binary_stdin_with_readonly_root_before_and_after_idle(self):self.exercise(real_photo=True)
    def test_real_photo_failure_is_not_hidden_by_successful_synthetic_probe(self):self.exercise('real-photo',real_photo=True)
    def test_questionnaire_is_checked_before_disposable_container_cleanup(self):self.exercise(questionnaire=True)
    def test_questionnaire_failure_prevents_success(self):self.exercise('question',questionnaire=True)


if __name__=='__main__':unittest.main()
