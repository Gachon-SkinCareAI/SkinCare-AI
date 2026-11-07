import json
from pathlib import Path
import tempfile
import unittest
from upgrade_free_demo import FILES, validate_release, validate_private_llm, validate_existing_volumes
from run_free_demo import checksum, compose_command, start_verified_services
from diagnose_local_llm import validate_container


class LocalServicesTests(unittest.TestCase):
    def test_startup_does_not_open_public_services_when_llm_preparation_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            calls=[]
            def run(command,name,timeout):
                calls.append((name,command))
                if name=='local-model-loaded-state':return '{"gpuLoaded":true}'
                if name.startswith('local-model-ready'):raise RuntimeError('model timed out')
                return ''
            with self.assertRaises(RuntimeError):
                start_verified_services(['docker','compose'],{'localLlmEnabled':True},run,Path(directory))
            self.assertNotIn('container-start',[name for name,_ in calls])
            self.assertEqual([name for name,_ in calls].count('local-model-ready-after-restart'),1)
            self.assertEqual(calls[0][1][-2:],['llm','ai'])

    def test_startup_opens_services_only_after_successful_model_check(self):
        with tempfile.TemporaryDirectory() as directory:
            calls=[]
            def run(command,name,timeout):
                calls.append(name)
                if name=='local-model-loaded-state':return '{"gpuLoaded":true}'
                return '{"event":"warmup"}\n{"passed":true,"gpuBytes":1}' if name in ('local-model-ready','vision-model-ready') else ''
            result=start_verified_services(['docker','compose'],{'localLlmEnabled':True},run,Path(directory))
            self.assertEqual(calls,['local-model-start','local-model-gpu-check','local-model-loaded-state',
                'local-model-ready','vision-model-ready','container-start'])
            self.assertTrue(result['passed'])
            self.assertTrue(json.loads((Path(directory)/'local-llm-startup-check.json').read_text())['passed'])

    def test_vision_timeout_keeps_backend_and_edge_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            calls=[]
            def run(command,name,timeout):
                calls.append(name)
                if name=='vision-model-ready':raise RuntimeError('first image timed out')
                return '{"gpuLoaded":true,"passed":true,"gpuBytes":1}'
            with self.assertRaises(RuntimeError):
                start_verified_services(['docker','compose'],{'localLlmEnabled':True},run,Path(directory))
            self.assertNotIn('container-start',calls)

    def test_diagnostics_reject_other_projects_public_ports_or_cloud_configuration(self):
        project='skincare-free-demo-12345678'
        info={'Config':{'Labels':{'com.docker.compose.project':project,'com.docker.compose.service':'llm'},
                        'Env':['OLLAMA_NO_CLOUD=1']},'HostConfig':{'PortBindings':{}},
              'NetworkSettings':{'Networks':{project+'_service':{}}}}
        validate_container(info,project,'llm')
        with self.assertRaises(RuntimeError):validate_container(info,'other','llm')
        info['HostConfig']['PortBindings']={'11434/tcp':[{'HostPort':'11434'}]}
        with self.assertRaises(RuntimeError):validate_container(info,project,'llm')
        info['HostConfig']['PortBindings']={};info['Config']['Env']=['OLLAMA_NO_CLOUD=0']
        with self.assertRaises(RuntimeError):validate_container(info,project,'llm')

    def test_storage_move_cannot_silently_create_a_new_database(self):
        project='skincare-free-demo-12345678'
        volumes=[{'Name':project+'_'+name,'Labels':{'com.docker.compose.project':project,
                  'com.docker.compose.volume':name}} for name in ('database','maintenance')]
        validate_existing_volumes(volumes,project)
        with self.assertRaises(RuntimeError):validate_existing_volumes([],project)
        with self.assertRaises(RuntimeError):validate_existing_volumes(volumes[:1],project)
        volumes[0]['Labels']['com.docker.compose.project']='other-project'
        with self.assertRaises(RuntimeError):validate_existing_volumes(volumes,project)

    def test_release_rejects_tampering_and_outside_allowlist(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();files={}
            for name in FILES:
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'fixture');files[name]=checksum(path)
            data={'format':'skincare-gps-local-llm-v1','files':files}
            (root/'release.json').write_text(json.dumps(data))
            validate_release(root)
            (root/'backend/app.jar').write_bytes(b'tampered')
            with self.assertRaises(RuntimeError):validate_release(root)
            data['files']['../outside']='wrong';(root/'release.json').write_text(json.dumps(data))
            with self.assertRaises(RuntimeError):validate_release(root)

    def test_private_network_and_cloud_disabled_are_required(self):
        data={'services':{'llm':{'networks':{'service':{}},'environment':{'OLLAMA_NO_CLOUD':'1'}},
             'ai':{'environment':{'LLM_BASE_URL':'http://llm:11434'}},
             'edge':{'ports':[{'host_ip':'127.0.0.1','published':'20880'}]}},'networks':{'service':{'internal':True}}}
        validate_private_llm(data)
        data['services']['llm']['networks']['outbound']={}
        with self.assertRaises(RuntimeError):validate_private_llm(data)
        del data['services']['llm']['networks']['outbound']
        data['services']['llm']['environment']['OLLAMA_NO_CLOUD']='0'
        with self.assertRaises(RuntimeError):validate_private_llm(data)

    def test_overlay_is_opt_in_and_hash_checked_for_start_and_stop(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);manifest={'deploymentMode':'temporary-tunnel','project':'skincare-free-demo-12345678','sources':{}}
            original=compose_command(root,manifest);self.assertNotIn(str(root/'compose.llm.yaml'),original)
            manifest['localLlmEnabled']=True
            with self.assertRaises(RuntimeError):compose_command(root,manifest)
            (root/'compose.llm.yaml').write_text('fixture');manifest['sources']['compose.llm.yaml']=checksum(root/'compose.llm.yaml')
            self.assertEqual(compose_command(root,manifest)[-2:],['-f',str(root/'compose.llm.yaml')])
            (root/'compose.llm.yaml').write_text('changed')
            with self.assertRaises(RuntimeError):compose_command(root,manifest)


if __name__=='__main__':unittest.main()
