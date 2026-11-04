"""Run from the user's host to validate an isolated Linux stack (no public deployment)."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT=Path(__file__).resolve().parent
manifest=json.loads((ROOT/'bundle-manifest.json').read_text(encoding='utf-8-sig'))
if manifest['mode']!='qa' or not manifest['project'].startswith('skincare-demo-qa-'):
    raise SystemExit('This verification runner only accepts an isolated QA bundle')
COMPOSE=['docker','compose','--env-file',str(ROOT/'.env'),'-p',manifest['project'],'-f',str(ROOT/'compose.yaml'),'-f',str(ROOT/'compose.qa.yaml')]
host_tmp=ROOT/'host-tmp'
host_tmp.mkdir(exist_ok=True)
if os.name=='nt':
    os.environ['TEMP']=str(host_tmp)
    os.environ['TMP']=str(host_tmp)

def status(step, **extra):
    (ROOT/'qa-status.json').write_text(json.dumps({'step':step,'time':time.time(),**extra},indent=2),encoding='utf-8')
    print(step,flush=True)

def run(args, filename, timeout=1800):
    status(filename)
    for attempt in range(2):
        with (ROOT/(filename+'.log')).open('ab' if attempt else 'wb') as log:
            result=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=timeout)
        text=(ROOT/(filename+'.log')).read_text(encoding='utf-8',errors='replace')
        retry=filename=='container-build' and result.returncode and 'compose-build-metadataFile-' in text and 'being used by another process' in text
        if not retry or attempt:
            break
        status('retry-build-metadata-file-lock')
        time.sleep(3)
    if result.returncode:
        raise RuntimeError(filename+' failed; inspect the private log')
    return (ROOT/(filename+'.log')).read_text(encoding='utf-8',errors='replace')

def request(path):
    try:
        with urllib.request.urlopen('http://127.0.0.1:19880'+path,timeout=10) as response:
            return response.status,response.read()
    except urllib.error.HTTPError as error:
        return error.code,error.read()

def main():
    try:
        run(['docker','version'],'docker-engine',30)
        config=json.loads(run(COMPOSE+['config','--format','json'],'compose-config',30))
        for name,service in config['services'].items():
            ports=service.get('ports',[])
            if name=='edge':
                assert len(ports)==1 and ports[0]['host_ip']=='127.0.0.1' and str(ports[0]['published'])=='19880'
            else:
                assert not ports, 'A private service publishes a host port'
        run(COMPOSE+['build'],'container-build',3600)
        run(COMPOSE+['run','--rm','--no-deps','edge','caddy','validate','--config','/etc/caddy/Caddyfile'],'caddy-config',120)
        run(COMPOSE+['up','-d','--wait','--wait-timeout','300'],'container-start',420)
        checks={}
        code,body=request('/api/wc/auth/social/providers')
        providers=json.loads(body)
        checks['providersEndpoint']=code==200 and {p['id'] for p in providers}=={'google','kakao','naver'} and all(not p['enabled'] for p in providers)
        code,body=request('/api/wc/auth/consent-policy')
        checks['versionedPolicyAvailable']=code==200 and json.loads(body)['requiredAvailable']
        checks['adminWeb']=request('/admin/')[0]==200
        checks['anonymousAnalysisDenied']=request('/api/wc/analyses')[0]==401
        checks['queryParametersAllowed']=request('/api/wc/auth/social/providers?probe=1')[0]==200
        checks['queryDoesNotBypassAuth']=request('/api/wc/analyses?probe=1')[0]==401
        checks['anonymousAdminApiDenied']=request('/api/wc/admin/audit')[0]==401
        checks['privateAiNotExposed']=request('/api/v1/ai/health')[0]==404
        checks['swaggerDisabled']=request('/v3/api-docs')[0]==404
        raw=run(COMPOSE+['exec','-T','maintenance','python3','/opt/demo/db_selftest.py'],'database-selftest',180)
        checks['databaseSelftest']=json.loads(raw.strip().splitlines()[-1])
        run(COMPOSE+['exec','-T','ai','python','/opt/demo/probe.py','ai'],'baseline-model-health',30)
        checks['baselineModelsLoaded']=True
        predict_code="""import io,json,time,urllib.request
from pathlib import Path
import numpy as np
from PIL import Image
buf=io.BytesIO(); Image.fromarray(np.random.default_rng(42).integers(40,210,(512,512,3),dtype=np.uint8)).save(buf,format='PNG')
boundary='skincare-software-test'; content=(('--'+boundary+'\\r\\nContent-Disposition: form-data; name="file"; filename="synthetic.png"\\r\\nContent-Type: image/png\\r\\n\\r\\n').encode()+buf.getvalue()+('\\r\\n--'+boundary+'--\\r\\n').encode())
token=Path('/run/secrets/ai_token').read_text().strip()
req=urllib.request.Request('http://127.0.0.1:8000/api/v1/ai/predict',data=content,headers={'Authorization':'Bearer '+token,'Content-Type':'multipart/form-data; boundary='+boundary})
start=time.perf_counter()
with urllib.request.urlopen(req,timeout=60) as response: result=json.load(response)
assert result['quality']['ok'] and result['classification'] is not None
print(json.dumps({'syntheticSoftwareInput':True,'clinicalAccuracyMeasured':False,'predictionApiCompleted':True,'elapsedSeconds':round(time.perf_counter()-start,3)}))
"""
        prediction=run(COMPOSE+['exec','-T','ai','python','-c',predict_code],'cpu-inference-smoke',90)
        checks['cpuPredictionSmoke']=json.loads(prediction.strip().splitlines()[-1])
        run(COMPOSE+['stop','maintenance'],'gate-stop',60)
        checks['maintenanceFailureBlocksPublication']=request('/api/wc/auth/social/providers')[0] in (502,503)
        run(COMPOSE+['up','-d','--wait','--wait-timeout','180','maintenance'],'gate-restart',240)
        for _ in range(10):
            if request('/api/wc/auth/social/providers')[0]==200:
                break
            time.sleep(1)
        checks['recoversAfterMaintenanceRestart']=request('/api/wc/auth/social/providers')[0]==200
        assert all(bool(value) for value in checks.values()),'A verification check failed'
        images=run(COMPOSE+['images','--format','json'],'images',30)
        run(COMPOSE+['stop'],'stop-qa',120)
        report={'passed':True,'scope':'isolated Linux containers, loopback only','checks':checks,
                'publicTlsVerified':False,'realProviderLoginVerified':False,'cloudPerformanceVerified':False,
                'volumesPreserved':True,'qaProject':manifest['project'],'images':images}
        (ROOT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        status('complete',passed=True)
    except Exception as error:
        # Diagnostics are limited to this generated QA project and stay in the
        # protected bundle. They contain no live application accounts or data.
        for args, filename in [(['ps','--all','--format','json'],'failure-container-state.log'),
                               (['logs','--no-color','--tail','120'],'failure-container-logs.log')]:
            try:
                with (ROOT/filename).open('wb') as log:
                    subprocess.run(COMPOSE+args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=30)
            except Exception:
                pass
        status('failed',error=str(error),passed=False)
        # Leave isolated QA state available for diagnosis; no volume deletion or production mutations.
        raise SystemExit(1)

if __name__=='__main__':
    main()
