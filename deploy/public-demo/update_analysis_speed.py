"""Update AI preparation/timings only, retaining the active tunnel and data volumes."""
import argparse
import json
import re
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request

from prepare_bundle import protect
from run_free_demo import HIDDEN,HERE,checksum,compose_command,instance_lock,json_write,verify_public_endpoints
from diagnose_analysis_speed import llm_timings

FILES={'ai/eval_classify.py','ai/vision/pipeline.py','ai/app/main.py','serve_ai.py',
       'backend/app.jar','compose.vision-cache.yaml'}
CACHE='vision_models_baseline_v1'


def stable_llm_checks(check,record,pause=time.sleep):
    """Deployment-only synthetic checks. Retain failures; require two consecutive passes."""
    history=[];consecutive=0;latest=None
    for attempt in range(1,4):
        item={'attempt':attempt,'passed':False}
        try:
            latest=check(attempt)
            turns=latest.get('turns',[])
            if not latest.get('passed') or not turns or any(
                    type(t.get('seconds')) not in (int,float) or not 0<=t['seconds']<=20
                    or t.get('allowed') is not True for t in turns):
                raise RuntimeError('Synthetic questionnaire did not satisfy the production latency gate')
            item.update(passed=True,maxTurnSeconds=max(t['seconds'] for t in turns))
            consecutive+=1
        except RuntimeError:
            # Each command log and failed attempt remain available; never relabel a failure.
            consecutive=0
        item['consecutivePasses']=consecutive;history.append(item);record(history)
        if consecutive==2:return {'policy':'two-consecutive-passes-max-three-attempts',
                                  'attempts':history,'lastSuccessfulCheck':latest}
        if attempt<3:pause(10)
    raise RuntimeError('Local LLM did not pass two consecutive bounded checks')


def image_reference(container):
    # containerd-backed engines may expose a config digest in Container.Image,
    # while Compose records the actual taggable image/index digest in this label.
    labels=container.get('Config',{}).get('Labels') or {}
    candidates=(labels.get('com.docker.compose.image'),
                (container.get('ImageManifestDescriptor') or {}).get('digest'),container.get('Image'))
    for value in candidates:
        if isinstance(value,str) and re.fullmatch(r'sha256:[0-9a-f]{64}',value):return value
    raise RuntimeError('No immutable image reference found for the existing container')


def same_image_content(running,candidate,image_id):
    def manifest(item):return (item.get('ImageManifestDescriptor') or {}).get('digest')
    if running.get('Image') and running['Image']==candidate.get('Image'):return 'container-image-digest'
    if manifest(running) and manifest(running)==manifest(candidate):return 'container-manifest-digest'
    declared=(running.get('Config',{}).get('Labels') or {}).get('com.docker.compose.image')
    if declared and declared in {image_id,candidate.get('Image'),manifest(candidate)}:
        return 'compose-recorded-image-digest'
    raise RuntimeError('The current image does not match the running container; no services were replaced')


def resolve_restore_image(container,project,service,command,save_identity):
    tag=container['Config']['Image']
    if tag not in {project+'-'+service,project+'-'+service+':latest'}:
        raise RuntimeError('Unexpected service image repository')
    info=json.loads(command(['docker','image','inspect',tag],'resolve-'+service+'-image',30,persist=False))[0]
    image_id=info.get('Id','')
    if not re.fullmatch(r'sha256:[0-9a-f]{64}',image_id):raise RuntimeError('Invalid resolved image ID')
    probe_id=None
    try:
        # Create, inspect and remove only a fresh, never-started, networkless container.
        # No secrets, models or data volumes are mounted and no application executes.
        probe_id=command(['docker','create','--pull','never','--network','none','--read-only',
            '--label','skincare.speed-image-check='+project,'--entrypoint','/bin/true',tag],
            'check-'+service+'-image-content',60).strip()
        if not re.fullmatch(r'[0-9a-f]{64}',probe_id):raise RuntimeError('Unexpected temporary container ID')
        probe=json.loads(command(['docker','inspect',probe_id],'inspect-'+service+'-image-content',30,persist=False))[0]
        labels=probe.get('Config',{}).get('Labels') or {}
        if labels.get('skincare.speed-image-check')!=project or probe['State']['Running']:
            raise RuntimeError('Unexpected temporary verification container')
        identity={'id':image_id,'tag':tag,'containerImageDigest':container.get('Image'),
            'containerManifestDigest':(container.get('ImageManifestDescriptor') or {}).get('digest'),
            'composeRecordedImage':(container['Config'].get('Labels') or {}).get('com.docker.compose.image'),
            'candidateImageDigest':probe.get('Image'),
            'candidateManifestDigest':(probe.get('ImageManifestDescriptor') or {}).get('digest')}
        save_identity(identity)
        identity['verifiedBy']=same_image_content(container,probe,image_id)
        current=json.loads(command(['docker','image','inspect',tag],'recheck-'+service+'-image',30,persist=False))[0]
        if current.get('Id')!=image_id:raise RuntimeError('Image tag changed during verification')
        return identity
    finally:
        if probe_id and re.fullmatch(r'[0-9a-f]{64}',probe_id):
            command(['docker','rm',probe_id],'remove-'+service+'-image-check',30)


def validate_release(path):
    release=json.loads((path/'release.json').read_text())
    if release.get('format')!='skincare-analysis-speed-v1' or set(release.get('files',{}))!=FILES:
        raise RuntimeError('Unexpected speed release file set')
    for name,expected in release['files'].items():
        if checksum(path/name)!=expected:raise RuntimeError('Speed release checksum mismatch')
    return release


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',required=True);parser.add_argument('--release',required=True)
    args=parser.parse_args();source=Path(args.release).resolve();release=validate_release(source)
    config=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format')!='skincare-free-demo-v1':raise RuntimeError('Unexpected config')
    root=Path(config['runtime']);out=Path(config['output']);bundle=root/'bundle'
    marker=bundle/'bundle-manifest.json';manifest=json.loads(marker.read_text())
    connection=json.loads((out/'public-connection.json').read_text())
    compose=compose_command(bundle,manifest);project=manifest['project'];origin=connection['origin']
    if release['project']!=project or not connection.get('active') or origin!='https://'+manifest['publicHost']:
        raise RuntimeError('Keep the expected existing public launcher running')
    if manifest.get('performanceRelease')==release['id']:
        if not manifest.get('visionModelCacheEnabled') or any(checksum(bundle/name)!=value for name,value in release['files'].items()):
            raise RuntimeError('Previously applied speed files have changed; review them before another update')
        print('This speed release is already applied. Keep the current public launcher running.');return
    for name,expected in release['baseFiles'].items():
        if name not in FILES or checksum(bundle/name)!=expected:raise RuntimeError('Current source changed; preserve it and review the release')
    operation=root/'analysis-speed-upgrade';operation.mkdir(exist_ok=True);protect(operation)
    report={'passed':False,'release':release['id'],'origin':origin,'project':project,
            'databaseChanged':False,'baselineModelReplaced':False,'tunnelRestarted':False,
            'publicUrlChanged':False,'realPhoneSpeedVerified':False}

    def launcher_active():
        current=json.loads((out/'public-connection.json').read_text())
        return current.get('active') and current.get('origin')==origin and not (root/'stop.request').exists()

    def require_launcher():
        if not launcher_active():raise RuntimeError('The public launcher was stopped; no further services will be started')

    def status(step):
        report['step']=step;json_write(out/'analysis-speed-upgrade.json',report);print(step,flush=True)

    def command(args,name,timeout=180,input_bytes=None,persist=True):
        status(name)
        started=time.monotonic()
        for attempt in range(2):
            result=subprocess.run(args,cwd=bundle,input=input_bytes,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                                  timeout=timeout,creationflags=HIDDEN)
            content=result.stdout.decode('utf-8',errors='replace')
            if persist:(operation/(name+'.log')).write_text(content,encoding='utf-8')
            if result.returncode==0:
                report.setdefault('stepSeconds',{})[name]=round(time.monotonic()-started,3)
                return content
            if name=='build-speed-services' and not attempt and 'compose-build-metadataFile-' in content and 'being used by another process' in content:
                time.sleep(3);continue
            raise RuntimeError(name+' failed; inspect the private step log')

    backup=operation/('before-'+str(time.time_ns()));old_images={};changed=False
    with instance_lock(operation):
        try:
            status('verify-existing-services')
            for service in ('ai','backend'):
                info=json.loads(command(['docker','inspect',project+'-'+service+'-1'],'inspect-'+service,30,persist=False))[0]
                labels=info['Config'].get('Labels') or {}
                if labels.get('com.docker.compose.project')!=project or labels.get('com.docker.compose.service')!=service or not info['State']['Running']:
                    raise RuntimeError('Unexpected existing service')
                old_images[service]=resolve_restore_image(info,project,service,command,
                    lambda identity:json_write(operation/(service+'-image-identity.json'),identity))
            # Only retain immutable image references, never raw inspect environments.
            for service in old_images:
                (operation/('inspect-'+service+'.log')).write_text(json.dumps(old_images[service]),encoding='utf-8')
                backup_tag=project+'-'+service+':before-speed-'+str(time.time_ns())
                command(['docker','tag',old_images[service]['tag'],backup_tag],'preserve-'+service+'-image',30)
                saved=json.loads(command(['docker','image','inspect',backup_tag],'verify-'+service+'-backup',30,persist=False))[0]
                if saved.get('Id')!=old_images[service]['id']:raise RuntimeError('Preserved image differs from the verified image')
                old_images[service]['backupTag']=backup_tag
                report.setdefault('preservedImageTags',{})[service]=backup_tag
            model_hashes={name:checksum(bundle/'models'/name) for name in ('detector.pt','classifier.pt','energy.json','manifest.json')}
            if model_hashes!=release['modelFiles']:raise RuntimeError('Baseline files changed since release preparation')
            cache_name=project+'_'+CACHE
            existing=subprocess.run(['docker','volume','inspect',cache_name],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                                    timeout=30,creationflags=HIDDEN)
            if existing.returncode==0:
                labels=json.loads(existing.stdout)[0].get('Labels') or {}
                if labels.get('com.docker.compose.project')!=project or labels.get('com.docker.compose.volume')!=CACHE:
                    raise RuntimeError('Existing model cache belongs to another deployment')
            else:
                command(['docker','volume','create','--label','com.docker.compose.project='+project,
                    '--label','com.docker.compose.volume='+CACHE,cache_name],'create-model-cache',30)
            cache=command(['docker','run','--rm','-i','--pull','never','--network','none','--read-only','--user','0:0','--cap-drop','ALL',
                '--security-opt','no-new-privileges:true','--mount','type=bind,source='+str(bundle/'models')+',target=/source,readonly',
                '--mount','type=volume,source='+cache_name+',target=/cache','--entrypoint','python',old_images['ai']['backupTag'],'-B','-'],
                'verify-and-cache-identical-models',420,(HERE/'init_vision_cache.py').read_bytes())
            report['modelCache']=json.loads(cache.strip().splitlines()[-1])
            require_launcher()
            backup.mkdir();shutil.copy2(marker,backup/'bundle-manifest.json')
            for name in FILES:
                target=bundle/name
                if target.exists():
                    saved=backup/name;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
            changed=True
            for name in FILES:shutil.copy2(source/name,bundle/name)
            manifest['visionModelCacheEnabled']=True
            manifest['sources']['compose.vision-cache.yaml']=checksum(bundle/'compose.vision-cache.yaml')
            compose=compose_command(bundle,manifest)
            parsed=json.loads(command(compose+['config','--format','json'],'validate-speed-compose',30,persist=False))
            mounts=[v for v in parsed['services']['ai']['volumes'] if v['target']=='/models']
            if len(mounts)!=1 or mounts[0]['type']!='volume' or mounts[0]['source']!=CACHE or not mounts[0].get('read_only'):
                raise RuntimeError('Model cache must be the only read-only /models mount')
            # Remove resolved compose output because it contains private connection settings.
            (operation/'validate-speed-compose.log').write_text('Validated immutable model cache mount.\n',encoding='utf-8')
            command(compose+['build','ai','backend'],'build-speed-services',1800)
            require_launcher()
            command(compose+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','420','ai'],'start-warmed-vision',480)
            command(compose+['exec','-T','ai','python','/opt/demo/probe.py','ai'],'baseline-model-health',30)
            runtime_log=command(compose+['logs','--no-color','--tail','150','ai'],'read-vision-preparation',30,persist=False)
            preparation=[]
            for line in runtime_log.splitlines():
                if '{' not in line:continue
                try:item=json.loads(line[line.index('{'):])
                except ValueError:continue
                if isinstance(item,dict) and item.get('event') in ('vision_load','vision_warmup'):
                    preparation.append({key:item[key] for key in ('event','device','elapsed_ms','detector_ms','classifier_ms','synthetic_input_only') if key in item})
            if {item['event'] for item in preparation}!={'vision_load','vision_warmup'}:
                raise RuntimeError('Both model loading and warmup must be verified')
            report['visionPreparation']=preparation
            require_launcher()
            command(compose+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','180','backend'],'start-timing-backend',240)
            def check_llm(attempt):
                require_launcher()
                smoke=command(compose+['exec','-T','ai','python','-B','-c',(HERE/'local_llm_smoke.py').read_text(encoding='utf-8')],
                              'llm-regression-'+str(attempt),120)
                return json.loads(smoke.strip().splitlines()[-1])
            def record_llm(history):
                report['llmReadinessAttempts']=history
                # Preserve numeric evidence before any rollback removes the candidate runtime.
                try:
                    raw=command(compose+['logs','--no-color','--since','5m','--tail','100','llm'],
                                'read-llm-readiness-timings',30,persist=False)
                    report['llmReadinessTimings']=llm_timings(raw)
                    raw=command(['docker','stats','--no-stream','--format','{{json .}}']+
                                [project+'-'+name+'-1' for name in ('ai','llm','backend','db')],
                                'read-llm-readiness-resources',30,persist=False)
                    report['llmReadinessResources']=[{key:item.get(key) for key in ('Name','CPUPerc','MemUsage','MemPerc','PIDs')}
                        for item in (json.loads(line) for line in raw.splitlines())]
                except RuntimeError:report['llmReadinessMetadataIncomplete']=True
                status('llm-readiness-check')
            report['llmReadiness']=stable_llm_checks(check_llm,record_llm)
            report['llm']=report['llmReadiness']['lastSuccessfulCheck']
            def get(path,public=False):
                try:
                    with urllib.request.urlopen((origin if public else 'http://127.0.0.1:20880')+path,timeout=10) as response:
                        return response.status,response.read()
                except urllib.error.HTTPError as error:return error.code,error.read()
            status('public-endpoint-check')
            report['endpoints']=verify_public_endpoints(get,lambda:None,
                lambda value:json_write(out/'analysis-speed-endpoints.json',{'origin':origin,**value}))
            if any(checksum(bundle/'models'/name)!=value for name,value in model_hashes.items()):
                raise RuntimeError('Original model checksum changed')
            current=json.loads((out/'public-connection.json').read_text())
            if not current.get('active') or current['origin']!=origin:raise RuntimeError('Public launcher changed during update')
            manifest['sources'].update(release['files']);manifest['performanceRelease']=release['id'];json_write(marker,manifest)
            report.update(passed=True,originalModelHashesPreserved=True,rollbackDirectory=str(backup))
            status('complete')
            print('Speed preparation updated. Public address and app v4 remain unchanged.',flush=True)
        except Exception as error:
            report.update(errorType=type(error).__name__,failedStep=report.get('step'))
            if changed:
                try:
                    status('restore-previous-services')
                    for name in FILES:
                        saved=backup/name
                        if saved.exists():shutil.copy2(saved,bundle/name)
                    shutil.copy2(backup/'bundle-manifest.json',marker)
                    manifest=json.loads(marker.read_text());compose=compose_command(bundle,manifest)
                    for item in old_images.values():command(['docker','tag',item['backupTag'],item['tag']],'restore-image-tag',30)
                    if launcher_active():
                        command(compose+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','420','ai','backend'],
                                'restore-service-runtime',480)
                    else:report['rollbackRuntimeSkipped']='User stopped the public launcher'
                    report['rollbackSucceeded']=True
                except Exception:report['rollbackSucceeded']=False
            status('failed')
            raise SystemExit(1)


if __name__=='__main__':main()
