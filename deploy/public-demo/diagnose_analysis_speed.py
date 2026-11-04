"""Bounded host performance check; preserve the running URL, DB and model files."""
import argparse
from datetime import datetime,timezone
import json
import math
from pathlib import Path
import re
import subprocess

from diagnose_local_llm import validate_container
from run_free_demo import HIDDEN,compose_command,json_write


def duration(value):
    return type(value) in (int,float) and math.isfinite(value) and value>=0


def timing_records(raw):
    records=[]
    allowed={'/api/v1/ai/predict','/api/v1/ai/question','/api/v1/ai/analyze','/api/v1/ai/guide'}
    for line in raw.splitlines():
        if '{' not in line:continue
        try:item=json.loads(line[line.index('{'):])
        except ValueError:continue
        if not isinstance(item,dict):continue
        if item.get('path') in allowed and duration(item.get('latency_ms')) and type(item.get('status')) is int and 100<=item['status']<=599:
            records.append({key:item[key] for key in ('path','status','latency_ms')})
    return records[-30:]


def vision_timings(raw):
    result=[]
    events={'vision_load':('elapsed_ms',),
            'vision_warmup':('detector_ms','classifier_ms'),
            'vision_timing':('total_ms','quality_ms','detector_ms','classifier_ms')}
    for line in raw.splitlines():
        if '{' not in line:continue
        try:item=json.loads(line[line.index('{'):])
        except ValueError:continue
        if not isinstance(item,dict) or item.get('event') not in events:continue
        entry={'event':item['event']}
        for key in events[item['event']]:
            if duration(item.get(key)):entry[key]=item[key]
        if len(entry)==1:continue
        if item.get('device') in ('cpu','cuda','mps'):entry['device']=item['device']
        if item.get('synthetic_input_only') is True:entry['synthetic_input_only']=True
        result.append(entry)
    return result[-50:]


def backend_timings(raw):
    result=[]
    request=re.compile(r'\bevent=analysis_request operation=(create|answer|retry) status=([1-5]\d{2}) completed=(true|false) elapsed_ms=(\d+)\s*$')
    ai=re.compile(r'\bevent=ai_request operation=(predict|question|analyze|guide) completed=(true|false) elapsed_ms=(\d+)\s*$')
    for line in raw.splitlines():
        match=request.search(line)
        if match:
            result.append({'event':'analysis_request','operation':match[1],'status':int(match[2]),
                           'completed':match[3]=='true','elapsed_ms':int(match[4])})
            continue
        match=ai.search(line)
        if match:
            result.append({'event':'ai_request','operation':match[1],'completed':match[2]=='true','elapsed_ms':int(match[3])})
    return result[-80:]


def llm_timings(raw):
    result=[]
    pattern=re.compile(r'\|\s*(\d{3})\s*\|\s*([\d.a-zµμ]+)\s*\|.*POST\s+"/api/chat"')
    scale={'ns':1e-9,'us':1e-6,'µs':1e-6,'μs':1e-6,'ms':1e-3,'s':1,'m':60,'h':3600}
    for line in raw.splitlines():
        match=pattern.search(line)
        if match:
            parts=re.findall(r'([\d.]+)(ns|us|µs|μs|ms|s|m|h)',match[2])
            if parts and ''.join(number+unit for number,unit in parts)==match[2]:
                try:seconds=sum(float(number)*scale[unit] for number,unit in parts)
                except ValueError:continue
                result.append({'status':int(match[1]),'seconds':round(seconds,3)})
    return result[-30:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True)
    parser.add_argument('--timings-only',action='store_true',help='Read existing numeric timings without generating requests or loading models')
    args=parser.parse_args();config=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format')!='skincare-free-demo-v1':raise RuntimeError('Unexpected launcher config')
    root=Path(config['runtime']);out=Path(config['output']);bundle=root/'bundle'
    manifest=json.loads((bundle/'bundle-manifest.json').read_text())
    connection=json.loads((out/'public-connection.json').read_text())
    compose=compose_command(bundle,manifest);project=manifest['project']
    if not connection.get('active') or connection.get('project')!=project:raise RuntimeError('Keep the current public launcher running')
    report={'checkedAt':datetime.now(timezone.utc).isoformat(),'origin':connection['origin'],'project':project,
        'databaseAccessed':False,'modelFilesChanged':False,'serviceRestarted':False,'publicUrlChanged':False,
        'syntheticInputOnly':not args.timings_only,'newInferenceRequested':not args.timings_only,
        'patientContentCollected':False,'containers':{}}
    destination=out/('analysis-speed-timings.json' if args.timings_only else 'analysis-speed-diagnostics.json')

    def run(command,timeout=60,input_bytes=None):
        result=subprocess.run(command,cwd=bundle,input=input_bytes,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                              timeout=timeout,creationflags=HIDDEN)
        if result.returncode:raise RuntimeError('Diagnostic command failed; no service changes were requested')
        return result.stdout.decode('utf-8',errors='replace')

    try:
        report['step']='inspect-running-services'
        print('inspect-running-services',flush=True)
        for service in ('ai','llm'):
            info=json.loads(run(['docker','inspect',project+'-'+service+'-1']))[0]
            validate_container(info,project,service)
            if not info['State']['Running']:raise RuntimeError('Expected AI/LLM services are not running')
            report['containers'][service]={'running':True,'health':info['State'].get('Health',{}).get('Status'),
                'oomKilled':info['State'].get('OOMKilled'),'memoryLimitBytes':info['HostConfig'].get('Memory'),
                'gpuRequested':bool(info['HostConfig'].get('DeviceRequests'))}
        report['dockerVmMemoryBytes']=json.loads(run(['docker','info','--format','{{json .MemTotal}}']))
        raw=run(['docker','stats','--no-stream','--format','{{json .}}']+[project+'-'+name+'-1' for name in ('ai','llm','backend','db')])
        report['resources']=[]
        for line in raw.splitlines():
            item=json.loads(line)
            report['resources'].append({key:item.get(key) for key in ('Name','CPUPerc','MemUsage','MemPerc','PIDs')})
        report['step']='read-existing-timings'
        print('read-existing-timings',flush=True)
        raw=run(compose+['logs','--no-color','--since','90m','--tail','1000','ai'])
        report['existingAiTimings']=timing_records(raw)
        report['visionTimings']=vision_timings(raw)
        raw=run(compose+['logs','--no-color','--since','90m','--tail','1000','backend'])
        report['backendTimings']=backend_timings(raw)
        raw=run(compose+['logs','--no-color','--since','90m','--tail','500','llm'])
        report['existingLlmTimings']=llm_timings(raw)
        if not args.timings_only:
            report['step']='measure-synthetic-predict-and-question'
            json_write(destination,report)
            print('measure-synthetic-predict-and-question',flush=True)
            raw=run(['docker','exec','-i',project+'-ai-1','python','-B','-'],360,
                    (Path(__file__).parent/'analysis_speed_probe.py').read_bytes())
            report['probe']=json.loads(raw.strip().splitlines()[-1])
        report['completed']=True
        report['step']='complete'
    except Exception as error:
        # Never save command stdout, credentials, patient text or raw HTTP bodies.
        report.update(completed=False,errorType=type(error).__name__)
    finally:json_write(destination,report)
    print('Speed diagnostics saved. No restart, database change, model download or public URL change.',flush=True)
    if not report.get('completed'):raise SystemExit(1)


if __name__=='__main__':main()
