"""Apply a bounded, reversible GPS/local-LLM release to the existing demo only."""
import argparse
import json
import re
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request
from run_free_demo import HIDDEN, checksum, compose_command, instance_lock, json_write, start_verified_services
from prepare_bundle import protect

FILES = frozenset({'ai/qa/llm_client.py', 'ai/qa/orchestrator.py', 'ai/app/routers/health.py',
                   'backend/app.jar', 'compose.llm.yaml'})


def validate_release(release):
    data=json.loads((release/'release.json').read_text(encoding='utf-8'))
    if data.get('format')!='skincare-gps-local-llm-v1' or set(data.get('files',{})) != FILES:
        raise RuntimeError('Unexpected release file allowlist')
    for name,digest in data['files'].items():
        file=(release/name).resolve()
        if not file.is_relative_to(release) or not file.is_file() or checksum(file)!=digest:
            raise RuntimeError('Release checksum mismatch: '+name)
    return data


def validate_private_llm(parsed):
    services=parsed['services'];llm=services['llm']
    if llm.get('ports') or set(llm['networks'])!={'service'}:
        raise RuntimeError('Local LLM must stay on the private service network')
    if llm['environment'].get('OLLAMA_NO_CLOUD')!='1':
        raise RuntimeError('Local LLM cloud features must be disabled')
    if not parsed['networks']['service'].get('internal'):
        raise RuntimeError('Local LLM network must be internal')
    if services['ai']['environment'].get('LLM_BASE_URL')!='http://llm:11434':
        raise RuntimeError('Unexpected LLM endpoint')
    for name,service in services.items():
        ports=service.get('ports',[])
        if name=='edge':
            if len(ports)!=1 or ports[0].get('host_ip')!='127.0.0.1' or str(ports[0]['published'])!='20880':
                raise RuntimeError('Unexpected edge port')
        elif ports:
            raise RuntimeError('Private service publishes a host port')


def validate_existing_volumes(volumes,project):
    expected={project+'_database':'database',project+'_maintenance':'maintenance'}
    if not isinstance(volumes,list) or len(volumes)!=len(expected):
        raise RuntimeError('Existing demo volumes were not found after the storage move; no new database will be created')
    seen=set()
    for volume in volumes:
        name=volume.get('Name');labels=volume.get('Labels') or {}
        if (name not in expected or name in seen or labels.get('com.docker.compose.project')!=project
                or labels.get('com.docker.compose.volume')!=expected[name]):
            raise RuntimeError('Unexpected database/maintenance volume ownership; update stopped')
        seen.add(name)


def capture_failure_runtime(project):
    """Read only runtime metadata, never database records or environment secrets."""
    if not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}',project):
        raise RuntimeError('Unexpected diagnostic project')
    report={}
    commands={
        'memory':['docker','stats','--no-stream','--format','{{json .}}',project+'-llm-1',project+'-ai-1'],
        'dockerMemory':['docker','info','--format','{{json .MemTotal}}'],
        'llmState':['docker','inspect','--format','{{json .State}}',project+'-llm-1'],
        'aiState':['docker','inspect','--format','{{json .State}}',project+'-ai-1'],
        'llmLog':['docker','logs','--timestamps','--tail','220',project+'-llm-1'],
    }
    for key,command in commands.items():
        try:
            result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                                  timeout=20,creationflags=HIDDEN)
            content=result.stdout.decode('utf-8',errors='replace')
            if key=='llmLog':
                pattern=re.compile(r'(?i)(gpu|cuda|memory|offload|runner|load|error|warn|/api/chat|cloud disabled)')
                content='\n'.join(line[:2500] for line in content.splitlines() if pattern.search(line))[-30000:]
            report[key]={'exitCode':result.returncode,'output':content[-30000:]}
        except Exception as error:report[key]={'errorType':type(error).__name__}
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',required=True)
    parser.add_argument('--release',required=True)
    parser.add_argument('--storage-drive',default='C:/')
    args=parser.parse_args()
    config=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format')!='skincare-free-demo-v1':raise RuntimeError('Unexpected launcher config')
    root=Path(config['runtime']).resolve();out=Path(config['output']).resolve();bundle=root/'bundle'
    release=Path(args.release).resolve();version=validate_release(release)
    report={'passed':False,'release':version['id'],'databaseReset':False,'baselineModelReplaced':False}
    logroot=root/'local-services-upgrade';logroot.mkdir(exist_ok=True);protect(logroot)
    def status(step):
        report.update(step=step,updatedAt=time.time());json_write(out/'local-services-upgrade.json',report);print(step,flush=True)
    def run(command,name,timeout=600,input_bytes=None):
        status(name);path=logroot/(name+'.log')
        for attempt in range(2):
            with path.open('ab' if attempt else 'wb') as log:
                result=subprocess.run(command,cwd=bundle,stdout=log,stderr=subprocess.STDOUT,
                    input=input_bytes,timeout=timeout,creationflags=HIDDEN)
            content=path.read_text(encoding='utf-8',errors='replace')
            if result.returncode==0:return content
            if (attempt==0 and name in {'build-services','restore-previous-images'}
                    and 'compose-build-metadataFile-' in content and 'being used by another process' in content):
                time.sleep(3);continue
            raise RuntimeError(name+' failed; inspect the private step log')
    with instance_lock(root):
        manifest_file=bundle/'bundle-manifest.json'
        old_manifest=json.loads(manifest_file.read_text())
        old_command=compose_command(bundle,old_manifest)
        if old_manifest.get('project') != version['project']:raise RuntimeError('Release belongs to a different demo project')
        backup=logroot/('backup-'+str(time.time_ns()));backup.mkdir()
        shutil.copy2(manifest_file,backup/'bundle-manifest.json')
        existing=[]
        for name in FILES:
            source=bundle/name
            if source.exists():
                target=backup/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);existing.append(name)
        (backup/'existing-files.json').write_text(json.dumps(existing))
        protected=[p for folder in ('models','private') for p in (bundle/folder).rglob('*') if p.is_file()]
        hashes={str(p):checksum(p) for p in protected}
        applied=False;command=old_command
        try:
            status('disk-preflight')
            free=shutil.disk_usage(args.storage_drive).free
            report['storageDrive']=args.storage_drive;report['freeGiB']=round(free/2**30,1)
            if free<20*2**30:raise RuntimeError('At least 20 GiB free is required on the Docker storage drive before downloading')
            run(['docker','version'],'docker-engine',30)
            project=old_manifest['project']
            volumes=json.loads(run(['docker','volume','inspect',project+'_database',project+'_maintenance'],
                                   'existing-data-volumes',30))
            validate_existing_volumes(volumes,project)
            report['existingDataVolumesFound']=True
            # Closed consoles may leave this project's loopback-only containers running.
            run(old_command+['stop'],'stop-existing-demo',180)
            connection_file=out/'public-connection.json'
            if connection_file.exists():
                connection=json.loads(connection_file.read_text(encoding='utf-8'))
                connection.update(active=False,stoppedForServiceUpdate=True)
                json_write(connection_file,connection)
            applied=True
            for name in FILES:
                target=bundle/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(release/name,target)
            manifest=dict(old_manifest);manifest['sources']=dict(old_manifest['sources'])
            manifest.update(localLlmEnabled=True,serviceRelease=version['id'],backendSha256=checksum(bundle/'backend/app.jar'))
            manifest['sources']['compose.llm.yaml']=checksum(bundle/'compose.llm.yaml')
            json_write(manifest_file,manifest);command=compose_command(bundle,manifest)
            parsed=json.loads(run(command+['config','--format','json'],'compose-config',30));validate_private_llm(parsed)
            # Pull through a separate setup container. Inference has no outbound network.
            run(command+['--profile','setup','run','--rm','--no-deps','llm-pull'],'download-local-model',3600)
            run(command+['build','ai','backend'],'build-services',3600)
            # Use the same preparation order as the public launcher. The successful
            # diagnostic ran AI + LLM alone; DB/backend must start after model preparation.
            report['startupLlm']=start_verified_services(command,manifest,run,out)
            run(command+['exec','-T','ai','python','/opt/demo/probe.py','ai'],'baseline-model-health',60)
            # Recheck real request budgets with the entire stack now running.
            smoke=run(command+['exec','-T','ai','python','-B','-'],'local-llm-smoke',480,
                      (Path(__file__).parent/'local_llm_smoke.py').read_bytes())
            report['llm']=json.loads(smoke.strip().splitlines()[-1])
            assert report['llm']['passed']
            with urllib.request.urlopen('http://127.0.0.1:20880/api/wc/auth/consent-policy',timeout=20) as response:
                policy=json.load(response)
            assert policy['requiredAvailable'] and policy['privacy']['version']=='2026-11-03'
            assert all(checksum(Path(p))==digest for p,digest in hashes.items()),'Protected files changed'
            report.update(passed=True,secretsAndBaselineFilesPreserved=True,rollbackDirectory=str(backup),
                          realPhoneTestPassed=False,clinicalValidation=False)
            run(command+['stop'],'stop-private-check',180)
            status('complete-ready-for-public-restart')
        except Exception as exc:
            report['error']=str(exc)
            if applied:
                json_write(logroot/'failure-runtime.json',capture_failure_runtime(old_manifest['project']))
                report['failureDiagnosticsSaved']=True
                try:
                    run(command+['stop'],'stop-failed-upgrade',180)
                    for name in FILES:
                        target=bundle/name
                        if name in existing:shutil.copy2(backup/name,target)
                        elif target.is_file():target.unlink() # Only a newly added allowlisted file.
                    shutil.copy2(backup/'bundle-manifest.json',manifest_file)
                    run(old_command+['build','ai','backend'],'restore-previous-images',3600)
                    report['previousReleaseRestored']=True
                except Exception as restore_error:report['restoreError']=str(restore_error)
            status('failed')
            raise SystemExit(1)


if __name__=='__main__':main()
