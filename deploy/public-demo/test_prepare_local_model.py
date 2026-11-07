import json
from pathlib import Path
import tempfile
import unittest

from prepare_local_model import prepare_local_model


class PrepareLocalModelTests(unittest.TestCase):
    def execute(self, *, loaded=False, failures=0):
        calls=[]
        def run(command, name, timeout):
            calls.append(name)
            if name=='local-model-loaded-state':return json.dumps({'gpuLoaded':loaded})
            if name.startswith('local-model-ready'):
                attempt=sum(n.startswith('local-model-ready') for n in calls)
                if attempt<=failures:raise RuntimeError('synthetic startup timeout')
                return '{"passed":true,"gpuBytes":100}'
            return ''
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'smoke.py';source.write_text('# synthetic')
            return prepare_local_model(['docker','compose'],run,source),calls

    def test_cold_gpu_is_awakened_then_model_server_refreshed(self):
        result,calls=self.execute()
        self.assertLess(calls.index('local-model-gpu-check'),calls.index('local-model-refresh-gpu'))
        self.assertLess(calls.index('local-model-refresh-gpu-healthy'),calls.index('local-model-ready'))
        self.assertTrue(result['coldGpuRefreshed'])

    def test_warm_gpu_model_is_not_unloaded(self):
        result,calls=self.execute(loaded=True)
        self.assertNotIn('local-model-refresh-gpu',calls)
        self.assertNotIn('local-model-recover-gpu',calls)

    def test_one_failed_startup_attempt_is_preserved_before_recovery(self):
        result,calls=self.execute(failures=1)
        self.assertEqual(result['preparationAttempts'],[{'attempt':1,'passed':False},{'attempt':2,'passed':True}])
        self.assertIn('local-model-failed-runtime',calls)
        self.assertEqual(calls.count('local-model-recover-gpu'),1)

    def test_repeated_startup_failure_is_not_accepted(self):
        with self.assertRaises(RuntimeError):self.execute(failures=2)

    def test_cold_health_wait_uses_startup_budget_without_rebuilding(self):
        commands=[]
        def run(command,name,timeout):
            commands.append((command,name,timeout))
            if name=='local-model-loaded-state':return '{"gpuLoaded":false}'
            if name=='local-model-ready':return '{"passed":true,"gpuBytes":100}'
            return ''
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'smoke.py';source.write_text('# synthetic')
            prepare_local_model(['docker','compose'],run,source)
        command,_,timeout=next(c for c in commands if c[1]=='local-model-refresh-gpu-healthy')
        self.assertEqual(command[command.index('--wait-timeout')+1],'600')
        self.assertIn('--no-build',command)
        self.assertEqual(command[command.index('--pull')+1],'never')
        self.assertEqual(timeout,660)

    def test_broken_exec_is_repaired_once_using_same_image_and_model_volume(self):
        commands=[]
        def run(command,name,timeout):
            commands.append((command,name))
            if name=='local-model-gpu-check':
                raise RuntimeError('OCI runtime exec failed: /proc/self/fd/6: no such file or directory')
            if name=='local-model-loaded-state':return '{"gpuLoaded":false}'
            if name=='local-model-ready':return '{"passed":true,"gpuBytes":100}'
            return ''
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'smoke.py';source.write_text('# synthetic')
            result=prepare_local_model(['docker','compose'],run,source)
        recreates=[c for c,n in commands if '--force-recreate' in c]
        self.assertEqual(len(recreates),1)
        self.assertEqual(recreates[0][-1],'llm')
        self.assertIn('--no-deps',recreates[0])
        self.assertIn('--no-build',recreates[0])
        self.assertEqual(recreates[0][recreates[0].index('--pull')+1],'never')
        self.assertFalse(any('restart' in c or 'down' in c or '--renew-anon-volumes' in c for c,n in commands))
        self.assertTrue(result['runtimeRecreated'])
        self.assertNotIn('local-model-refresh-gpu',[n for c,n in commands])


if __name__=='__main__':unittest.main()
