"""Bounded local LLM diagnostic. Reuses existing stopped containers and cached model."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
from run_free_demo import HIDDEN,compose_command,instance_lock,json_write
from prepare_bundle import protect


def validate_container(info,project,service):
    labels=info.get('Config',{}).get('Labels') or {}
    if labels.get('com.docker.compose.project')!=project or labels.get('com.docker.compose.service')!=service:
        raise RuntimeError('Unexpected diagnostic container ownership')
    if info.get('HostConfig',{}).get('PortBindings'):
        raise RuntimeError('Diagnostic services must not publish ports')
    if set(info.get('NetworkSettings',{}).get('Networks',{}))!={project+'_service'}:
        raise RuntimeError('Diagnostic services must stay on the private network')
    env=dict(item.split('=',1) for item in info['Config'].get('Env',[]) if '=' in item)
    required={'LLM_PROVIDER':'ollama','LLM_BASE_URL':'http://llm:11434','LLM_MODEL':'qwen3:4b'} if service=='ai' else {'OLLAMA_NO_CLOUD':'1'}
    if any(env.get(k)!=v for k,v in required.items()):
        raise RuntimeError('Expected local-only LLM configuration was not found')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True);args=parser.parse_args()
    config=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format')!='skincare-free-demo-v1':raise RuntimeError('Unexpected config')
    root=Path(config['runtime']);out=Path(config['output']);bundle=root/'bundle'
    manifest=json.loads((bundle/'bundle-manifest.json').read_text());compose_command(bundle,manifest)
    project=manifest['project'];private=root/'local-llm-diagnostics';private.mkdir(exist_ok=True);protect(private)
    report={'project':project,'checkedAt':time.time(),'databaseAccessed':False,'modelDownloaded':False,
            'imagesBuilt':False,'publicUrlChanged':False,'syntheticInputOnly':True}
    def run(args,timeout=30,input_bytes=None):
        result=subprocess.run(['docker']+args,input=input_bytes,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                              timeout=timeout,creationflags=HIDDEN)
        return result.returncode,result.stdout.decode('utf-8',errors='replace')
    def logs():
        code,content=run(['logs','--timestamps','--tail','220',project+'-llm-1'])
        # Ollama logs model loading/requests, not prompts. Retain only runtime diagnostic lines.
        pattern=re.compile(r'(?i)(gpu|cuda|memory|offload|runner|load|error|warn|/api/chat|/api/generate|cloud disabled)')
        return {'exitCode':code,'lines':[line[:2500] for line in content.splitlines() if pattern.search(line)][-160:]}
    started=[]
    with instance_lock(root):
        try:
            print('inspect-existing-local-model',flush=True)
            infos={}
            for service in ('llm','ai'):
                code,raw=run(['inspect',project+'-'+service+'-1'])
                if code:raise RuntimeError('Existing '+service+' container could not be inspected; nothing rebuilt')
                info=json.loads(raw)[0];validate_container(info,project,service);infos[service]=info
                report[service]={'state':{k:info['State'].get(k) for k in ('Status','Running','OOMKilled','ExitCode','StartedAt','FinishedAt')},
                    'memoryLimitBytes':info['HostConfig'].get('Memory'),'deviceRequests':info['HostConfig'].get('DeviceRequests')}
            report['previousRuntimeLog']=logs()
            code,raw=run(['info','--format','{{json .MemTotal}}']);report['dockerMemoryBytes']=json.loads(raw) if code==0 else None
            json_write(private/'report.json',report)
            for service in ('llm','ai'):
                if not infos[service]['State']['Running']:
                    name=project+'-'+service+'-1';started.append(name)
                    code,_=run(['start',name],60)
                    if code:raise RuntimeError('Could not start existing '+service+' diagnostic container')
            print('synthetic-llm-probe-no-patient-data',flush=True)
            code,raw=run(['exec','-i',project+'-ai-1','python','-B','-'],330,
                         (Path(__file__).parent/'local_llm_diagnostic_probe.py').read_bytes())
            if code:report['probeProcessError']=raw[-2000:]
            else:report['probe']=json.loads(raw.strip().splitlines()[-1])
            report['currentRuntimeLog']=logs()
        except Exception as error:report['error']=str(error)
        finally:
            if started:
                print('restore-original-stopped-state',flush=True)
                code,_=run(['stop','--time','15']+list(reversed(started)),90)
                report['restoredStoppedState']=code==0
            json_write(private/'report.json',report)
            json_write(out/'local-llm-diagnostics-status.json',{'reportWritten':True,'checkedAt':report['checkedAt'],
                'error':report.get('error'),'databaseAccessed':False,'modelDownloaded':False,'imagesBuilt':False,
                'restoredStoppedState':report.get('restoredStoppedState')})
    print('Local LLM diagnostics saved. No database, model files or public URL was changed.',flush=True)


if __name__=='__main__':main()
