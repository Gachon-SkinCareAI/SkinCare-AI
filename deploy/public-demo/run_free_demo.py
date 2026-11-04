"""Bounded Windows host launcher: isolated demo DB + temporary public HTTPS."""
import argparse
from contextlib import contextmanager
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

HERE=Path(__file__).resolve().parent
HIDDEN=getattr(subprocess,'CREATE_NO_WINDOW',0)

class StopRequested(Exception):
    pass

class AlreadyRunning(RuntimeError):
    pass

def compose_command(bundle,manifest):
    if manifest.get('deploymentMode')!='temporary-tunnel' or not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}',manifest.get('project','')):
        raise RuntimeError('Refusing to operate on a different deployment bundle.')
    command=['docker','compose','--env-file',str(bundle/'.env'),'-p',manifest['project'],
             '-f',str(bundle/'compose.yaml'),'-f',str(bundle/'compose.tunnel.yaml')]
    if manifest.get('localLlmEnabled'):
        overlay=bundle/'compose.llm.yaml'
        if not overlay.is_file() or checksum(overlay)!=manifest['sources'].get('compose.llm.yaml'):
            raise RuntimeError('Local LLM overlay checksum mismatch.')
        command += ['-f',str(overlay)]
    if manifest.get('visionModelCacheEnabled'):
        overlay=bundle/'compose.vision-cache.yaml'
        if not overlay.is_file() or checksum(overlay)!=manifest['sources'].get('compose.vision-cache.yaml'):
            raise RuntimeError('Vision cache overlay checksum mismatch.')
        command += ['-f',str(overlay)]
    return command

def checksum(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for part in iter(lambda:stream.read(1048576),b''):h.update(part)
    return h.hexdigest()

def tunnel_origin(text):
    found=re.search(r'https://([a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com)(?=[\s|]|$)',text)
    return 'https://'+found.group(1) if found else None

def json_write(path,value):
    temp=path.with_suffix('.tmp')
    temp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    temp.replace(path)


def verify_public_endpoints(get,check_stop,record,*,timeout=90,clock=time.monotonic,sleep=time.sleep):
    """Check both routes; retry transient readiness failures without relaxing contracts.

    The report intentionally contains no response bodies, headers or exception text.
    These are read-only anonymous GETs; questionnaire submissions are never retried.
    """
    def providers_valid(body):
        providers=json.loads(body)
        return (isinstance(providers,list) and len(providers)==3
                and {p['id'] for p in providers}=={'google','kakao','naver'}
                and all(p['enabled'] is True for p in providers))

    def policy_valid(body):
        return json.loads(body)['requiredAvailable'] is True

    probes=(
        ('ProvidersEnabled','/api/wc/auth/social/providers',200,providers_valid),
        ('QueryParametersAllowed','/api/wc/auth/social/providers?probe=1',200,providers_valid),
        ('QueryDoesNotBypassAuth','/api/wc/analyses?probe=1',401,None),
        ('PolicyAvailable','/api/wc/auth/consent-policy',200,policy_valid),
        ('AnonymousAnalysisDenied','/api/wc/analyses',401,None),
        ('PrivateAiBlocked','/api/v1/ai/health',404,None),
        ('SwaggerBlocked','/v3/api-docs',404,None),
    )
    started=clock();deadline=started+timeout
    report={'passed':False,'checks':{},'attempts':[]}
    record(report)
    for public in (False,True):
        scope='public' if public else 'local'
        for name,path,expected,validate in probes:
            key=scope+name
            while True:
                check_stop()
                code=None;error_type=None;valid=False
                try:
                    code,body=get(path,public)
                except OSError as error:
                    error_type=type(error).__name__
                if code==expected:
                    try:valid=validate is None or validate(body)
                    except (ValueError,TypeError,KeyError):valid=False
                transient=error_type is not None or code in (502,503,504,530)
                outcome='passed' if valid else ('not-ready' if transient else 'contract-failed')
                report['attempts'].append({'scope':scope,'check':name,'path':path,
                    'expectedStatus':expected,'status':code,'outcome':outcome,
                    'errorType':error_type,'elapsedSeconds':round(clock()-started,3)})
                report['checks'][key]=bool(valid)
                if valid:
                    record(report)
                    break
                if not transient or clock()>=deadline:
                    actual=str(code) if code is not None else error_type
                    reason='readiness timeout' if transient else ('invalid response contract' if code==expected else 'unexpected HTTP status')
                    message=f'{key}: {reason}; expected HTTP {expected}, received {actual}'
                    report['failure']=message
                    record(report)
                    raise RuntimeError(message)
                record(report)
                sleep(min(2,max(0,deadline-clock())))
    report['passed']=True
    record(report)
    return report['checks']


def start_verified_services(compose,manifest,run,out):
    result=None
    if manifest.get('localLlmEnabled'):
        # Keep edge/backend closed until synthetic questionnaire requests succeed.
        # Every restart repeats this because GPU state is lost when containers stop.
        run(compose+['up','-d','--wait','--wait-timeout','300','llm','ai'],'local-model-start',420)
        smoke=run(compose+['exec','-T','ai','python','-B','-c',
            (HERE/'local_llm_smoke.py').read_text(encoding='utf-8')],'local-model-ready',480)
        result=json.loads(smoke.strip().splitlines()[-1])
        if result.get('passed') is not True:raise RuntimeError('Local LLM startup verification failed')
        json_write(out/'local-llm-startup-check.json',result)
    run(compose+['up','-d','--wait','--wait-timeout','300'],'container-start',420)
    return result

@contextmanager
def instance_lock(root):
    import msvcrt
    with (root/'launcher.lock').open('a+b') as stream:
        stream.seek(0);stream.write(b'1');stream.flush();stream.seek(0)
        try:msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1)
        except OSError:raise AlreadyRunning('The free demo launcher is already running.')
        try:yield
        finally:
            stream.seek(0);msvcrt.locking(stream.fileno(),msvcrt.LK_UNLCK,1)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',required=True)
    parser.add_argument('--stop',action='store_true')
    args=parser.parse_args()
    config=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format')!='skincare-free-demo-v1':raise SystemExit('Unexpected launcher configuration')
    root=Path(config['runtime']).resolve();out=Path(config['output']).resolve()
    root.mkdir(parents=True,exist_ok=True);out.mkdir(parents=True,exist_ok=True)
    stop=root/'stop.request'
    if args.stop:
        stop.touch()
        try:
            with instance_lock(root):
                bundle=root/'bundle';marker=bundle/'bundle-manifest.json'
                if marker.exists():
                    command=compose_command(bundle,json.loads(marker.read_text()))
                    with (root/'stop-demo.log').open('wb') as log:
                        result=subprocess.run(command+['stop'],cwd=bundle,stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=HIDDEN)
                    if result.returncode:raise RuntimeError('Demo stop failed; inspect the private stop log.')
                json_write(out/'status.json',{'step':'stopped','updatedAt':time.time(),'tunnelDisconnected':True,'volumesPreserved':True})
                print('Demo stopped. Data volumes were preserved.')
        except AlreadyRunning:
            print('Stop requested. The running launcher will disconnect the tunnel and stop only its demo containers.')
        return
    if os.name!='nt':raise SystemExit('Use the Windows host launcher for this demo.')
    with instance_lock(root):
        from prepare_bundle import protect
        protect(root)
        if stop.exists():stop.unlink()
        temp=root/'host-tmp';temp.mkdir(exist_ok=True)
        env=os.environ.copy();env.update(TEMP=str(temp),TMP=str(temp))
        for key in tuple(env):
            if key.startswith('TUNNEL_') or key.startswith('CLOUDFLARE_') or key.startswith('CLOUDFLARED_'):
                env.pop(key,None)
        tunnel=None;job=None;compose=None;bundle=root/'bundle';failure=None;url=None;awake=False
        def status(step,**extra):
            json_write(out/'status.json',{'step':step,'updatedAt':time.time(),'url':url,**extra})
            print(step,flush=True)
        def check_stop():
            if stop.exists():raise StopRequested()
            if tunnel is not None and tunnel.poll() is not None:raise RuntimeError('Temporary HTTPS tunnel stopped. Inspect the private tunnel log.')
        def run(command,name,timeout=1800):
            status(name)
            path=root/(name+'.log')
            for attempt in range(2):
                with path.open('ab' if attempt else 'wb') as log:
                    process=subprocess.Popen(list(map(str,command)),cwd=bundle if bundle.exists() else root,
                        env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=HIDDEN)
                    deadline=time.monotonic()+timeout
                    try:
                        while process.poll() is None:
                            check_stop()
                            if time.monotonic()>deadline:raise RuntimeError(name+' timed out')
                            time.sleep(.5)
                    finally:
                        if process.poll() is None:process.terminate();process.wait(timeout=15)
                text=path.read_text(encoding='utf-8',errors='replace')
                if process.returncode==0:return text
                if attempt==0 and name=='container-build' and 'compose-build-metadataFile-' in text and 'being used by another process' in text:
                    time.sleep(3);continue
                raise RuntimeError(name+' failed; inspect its private log.')
        def get(path,public=False):
            req=urllib.request.Request((url if public else 'http://127.0.0.1:20880')+path)
            try:
                with urllib.request.urlopen(req,timeout=10) as response:return response.status,response.read()
            except urllib.error.HTTPError as error:return error.code,error.read()
        try:
            for name in ('config.yaml','config.yml'):
                if (Path.home()/'.cloudflared'/name).exists():
                    raise RuntimeError('Existing cloudflared configuration needs review; it was not modified.')
            try:
                with socket.socket() as probe:probe.bind(('127.0.0.1',20880))
            except OSError:
                raise RuntimeError('Port 20880 is in use. If an old demo console was closed, run Stop-Free-Demo.cmd before restarting.')
            binary=Path(config['cloudflared'])
            if checksum(binary)!=config['cloudflaredSha256']:raise RuntimeError('Tunnel binary checksum mismatch')
            run(['docker','version'],'docker-engine',30)
            from windows_tunnel_job import TunnelJob
            job=TunnelJob()
            status('requesting-temporary-https')
            tunnel_log=root/'tunnel.log'
            with tunnel_log.open('wb') as log:
                tunnel=subprocess.Popen([str(binary),'tunnel','--no-autoupdate','--url','http://127.0.0.1:20880',
                    '--metrics','127.0.0.1:0'],cwd=root,env=env,stdin=subprocess.DEVNULL,
                    stdout=log,stderr=subprocess.STDOUT,creationflags=HIDDEN)
            job.attach(tunnel)
            deadline=time.monotonic()+90
            while not url and time.monotonic()<deadline:
                check_stop();url=tunnel_origin(tunnel_log.read_text(encoding='utf-8',errors='replace'));time.sleep(.5)
            if not url:raise RuntimeError('No temporary HTTPS address was issued; inspect the tunnel log.')
            host=url.removeprefix('https://')
            manifest_file=bundle/'bundle-manifest.json'
            if not manifest_file.exists():
                run([sys.executable,'-B',str(HERE/'prepare_bundle.py'),'--mode','public','--hostname',host,
                     '--email',config['contactEmail'],'--local-app-config',config['localAppConfig'],'--output',str(bundle)],'prepare-isolated-bundle',180)
                manifest=json.loads(manifest_file.read_text())
                manifest.update(project='skincare-free-demo-'+secrets.token_hex(4),deploymentMode='temporary-tunnel')
            else:
                manifest=json.loads(manifest_file.read_text())
                if manifest.get('deploymentMode')!='temporary-tunnel' or not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}',manifest.get('project','')):
                    raise RuntimeError('Refusing to adopt a different deployment bundle.')
                if manifest.get('mode')!='public' or not manifest.get('providerSecretsIncluded'):
                    raise RuntimeError('Unexpected demo bundle mode.')
            for name in ('compose.tunnel.yaml','Caddyfile.tunnel'):
                shutil.copy2(HERE/name,bundle/name)
                manifest['sources'][name]=checksum(bundle/name)
            manifest['publicHost']=host
            json_write(manifest_file,manifest)
            (bundle/'.env').write_bytes(('PUBLIC_HOST='+host+'\nTLS_EMAIL='+config['contactEmail']+'\n').encode('ascii'))
            compose=compose_command(bundle,manifest)
            parsed=json.loads(run(compose+['config','--format','json'],'compose-config',30))
            for name,service in parsed['services'].items():
                ports=service.get('ports',[])
                if name=='edge':
                    assert len(ports)==1 and ports[0]['host_ip']=='127.0.0.1' and str(ports[0]['published'])=='20880'
                else:assert not ports,'Private service unexpectedly publishes a host port'
            assert parsed['services']['backend']['environment']['SERVER_SERVLET_SESSION_COOKIE_SECURE']=='true'
            run(compose+['build'],'container-build',3600)
            run(compose+['run','--rm','--no-deps','edge','caddy','validate','--config','/etc/caddy/Caddyfile'],'caddy-config',120)
            start_verified_services(compose,manifest,run,out)
            status('endpoint-check')
            def record_endpoints(report):
                json_write(out/'public-endpoint-checks.json',{'url':url,'updatedAt':time.time(),**report})
            checks=verify_public_endpoints(get,check_stop,record_endpoints)
            run(compose+['exec','-T','ai','python','/opt/demo/probe.py','ai'],'baseline-model-health',30)
            callbacks={provider:url+'/login/oauth2/code/'+provider for provider in ('google','kakao','naver')}
            json_write(out/'public-connection.json',{'active':True,'origin':url,'callbacks':callbacks,'nativeCallback':'skincareai://oauth/callback',
                'adminUrl':url+'/admin/','project':manifest['project'],'endpointChecks':checks,
                'realSocialLoginVerified':False,'publicApkBuilt':False,'temporary':True})
            (out/'OAuth-콜백-주소.txt').write_text('실행 중인 임시 HTTPS 주소: '+url+'\n\n'+
                '\n'.join(provider+': '+callback for provider,callback in callbacks.items())+
                '\n\n기존 PC/USB 콜백은 보존하고 위 주소를 추가하세요. 실제 로그인은 아직 검증 전입니다.\n'+
                '터널 재시작 시 주소가 바뀌므로 이 파일의 최신 주소를 사용하세요.\n',encoding='utf-8')
            awake=bool(ctypes.windll.kernel32.SetThreadExecutionState(0x80000001))
            status('ready-awaiting-oauth-registration',checks=checks,preventsIdleSleep=awake)
            print('Public demo: '+url,flush=True)
            print('Keep this window and Docker running while presenting at school. Use Stop-Free-Demo.cmd to disconnect.',flush=True)
            while not stop.exists():
                check_stop();time.sleep(1)
        except (KeyboardInterrupt,StopRequested):
            pass
        except Exception as error:
            failure=str(error)
            if compose:
                for command,name in [(['ps','--all','--format','json'],'failure-state.log'),(['logs','--no-color','--tail','100'],'failure-containers.log')]:
                    try:
                        with (root/name).open('wb') as log:
                            subprocess.run(compose+command,cwd=bundle,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=30,creationflags=HIDDEN)
                    except Exception:pass
        finally:
            if tunnel is not None and tunnel.poll() is None:
                tunnel.terminate()
                try:tunnel.wait(timeout=15)
                except subprocess.TimeoutExpired:tunnel.kill();tunnel.wait(timeout=5)
            if job is not None:job.close()
            if compose:
                try:
                    with (root/'stop-demo.log').open('wb') as log:
                        result=subprocess.run(compose+['stop'],cwd=bundle,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=HIDDEN)
                        if result.returncode:failure=failure or 'Demo container stop failed; tunnel was disconnected.'
                except Exception:failure=failure or 'Demo container stop could not finish; tunnel was disconnected.'
            if awake:ctypes.windll.kernel32.SetThreadExecutionState(0x80000000)
            if (out/'public-connection.json').exists():
                connection=json.loads((out/'public-connection.json').read_text(encoding='utf-8'))
                connection['active']=False
                json_write(out/'public-connection.json',connection)
            status('failed' if failure else 'stopped',error=failure,tunnelDisconnected=True,volumesPreserved=True)
        if failure:raise SystemExit(1)

if __name__=='__main__':
    main()
