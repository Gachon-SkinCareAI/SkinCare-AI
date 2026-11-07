import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from diagnose_public_models import capture


class PublicModelDiagnosticsTests(unittest.TestCase):
    def fixture(self, fail_ps=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'bundle').mkdir()
            project = 'skincare-free-demo-12345678'
            (root/'bundle/bundle-manifest.json').write_text(json.dumps({'project':project}))
            (root/'config.json').write_text(json.dumps({'format':'skincare-free-demo-v1','runtime':str(root)}))
            (root/'public-status.json').write_text(json.dumps({'origin':'https://fixture.trycloudflare.com'}))
            calls=[]
            def execute(args, **kwargs):
                calls.append(args)
                if args[1]=='inspect':
                    service=args[2].split('-')[-2]
                    value=[{'Config':{'Labels':{'com.docker.compose.project':project,'com.docker.compose.service':service},'Env':['secret-env']},
                        'State':{'Running':True,'StartedAt':'fixture','Health':{'Status':'starting','Log':[{'ExitCode':1,'Output':'connection refused'}]}},
                        'RestartCount':0,'HostConfig':{'Memory':100}}]
                    output=json.dumps(value)
                elif args[1]=='info':output='{"memoryBytes":8000000000,"cpus":4}'
                elif args[1]=='stats':output='{"Name":"fixture","CPUPerc":"0%"}'
                elif args[1]=='logs':output='{"prompt":"secret-patient-content"}\nheadroom="7.5 GiB"\nload_mode = mmap\nmsg="Load failed" timed out waiting for llama-server to start'
                elif args[3]=='python':output='{"MemAvailable":"1234 kB"}'
                else:
                    if fail_ps:return SimpleNamespace(returncode=1,stdout=b'connection refused')
                    output='NAME ID SIZE PROCESSOR'
                return SimpleNamespace(returncode=0,stdout=output.encode())
            with patch('diagnose_public_models.subprocess.run',side_effect=execute):
                result=capture(root/'config.json',root)
            saved=(root/'public-model-diagnostics.json').read_text()
            self.assertNotIn('secret-env',saved)
            self.assertNotIn('secret-patient-content',saved)
            self.assertTrue(all(args[1] in {'inspect','info','stats','logs','exec'} for args in calls))
            return result

    def test_health_reason_is_recorded_without_patient_logs(self):
        result=self.fixture()
        self.assertTrue(result['completed'])
        self.assertEqual(result['llmHealthChecks'][0]['Output'],'connection refused')

    def test_unavailable_model_still_preserves_health_evidence(self):
        result=self.fixture(fail_ps=True)
        self.assertFalse(result['completed'])
        self.assertEqual(result['llmHealthChecks'][0]['ExitCode'],1)

    def test_memory_headroom_is_not_reported_as_oom_and_load_failure_is_preserved(self):
        result=self.fixture()
        self.assertEqual(result['errors']['llm']['oom'],0)
        self.assertEqual(result['modelLoading']['fileMappedLoads'],1)
        self.assertEqual(result['modelLoading']['loadTimeouts'],1)
        self.assertEqual(result['modelLoading']['loadFailures'],1)


if __name__=='__main__':unittest.main()
