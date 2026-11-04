"""Read live resource/timing metadata and profile three fixed synthetic LLM inputs."""
import argparse
from datetime import datetime,timezone
import json
from pathlib import Path
import subprocess

from diagnose_analysis_speed import llm_timings,timing_records,vision_timings,backend_timings
from diagnose_local_llm import validate_container
from run_free_demo import HIDDEN,compose_command,json_write,instance_lock


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True)
    args=parser.parse_args();config=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format')!='skincare-free-demo-v1':raise RuntimeError('Unexpected launcher config')
    root=Path(config['runtime']);out=Path(config['output']);bundle=root/'bundle'
    manifest=json.loads((bundle/'bundle-manifest.json').read_text());compose=compose_command(bundle,manifest)
    connection=json.loads((out/'public-connection.json').read_text());project=manifest['project']
    if not connection.get('active') or connection.get('project')!=project:raise RuntimeError('Keep the current launcher running')
    report={'checkedAt':datetime.now(timezone.utc).isoformat(),'project':project,'origin':connection['origin'],
            'databaseAccessed':False,'modelFilesChanged':False,'serviceRestarted':False,
            'publicUrlChanged':False,'syntheticInputOnly':True,'patientContentCollected':False}
    destination=out/'llm-latency-diagnostics.json'
    def run(command,timeout=30,input_bytes=None):
        result=subprocess.run(command,cwd=bundle,input=input_bytes,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                              timeout=timeout,creationflags=HIDDEN)
        if result.returncode:raise RuntimeError('Diagnostic command failed')
        return result.stdout.decode('utf-8',errors='replace')
    def status(step):report['step']=step;json_write(destination,report);print(step,flush=True)
    def readings():
        result={}
        for service,parsers in (('ai',{'ai':timing_records,'vision':vision_timings}),
                                ('llm',{'llm':llm_timings}),('backend',{'backend':backend_timings})):
            raw=run(compose+['logs','--no-color','--since','45m','--tail','1000',service])
            for key,parse in parsers.items():result[key]=parse(raw)
        raw=run(['docker','stats','--no-stream','--format','{{json .}}']+
                [project+'-'+service+'-1' for service in ('ai','llm','backend','db')])
        result['resources']=[{key:item.get(key) for key in ('Name','CPUPerc','MemUsage','MemPerc','PIDs')}
                             for item in (json.loads(line) for line in raw.splitlines())]
        try:
            raw=run(['docker','exec',project+'-llm-1','nvidia-smi',
                '--query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu','--format=csv,noheader,nounits'],15)
            result['gpu']=[]
            for line in raw.splitlines():
                values=[value.strip() for value in line.split(',')]
                if len(values)==4 and all(value.isdigit() for value in values):
                    result['gpu'].append(dict(zip(('utilizationPercent','memoryUsedMiB','memoryTotalMiB','temperatureC'),map(int,values))))
        except Exception:result['gpuStatsAvailable']=False
        return result
    operation=root/'llm-latency-diagnostics';operation.mkdir(exist_ok=True)
    with instance_lock(operation):
        try:
            status('verify-running-private-services')
            for service in ('ai','llm'):
                info=json.loads(run(['docker','inspect',project+'-'+service+'-1']))[0]
                validate_container(info,project,service)
                if not info['State']['Running']:raise RuntimeError('Required service is stopped')
            status('read-current-timings');report['before']=readings()
            status('measure-synthetic-question-latency')
            raw=run(['docker','exec','-i',project+'-ai-1','python','-B','-'],210,
                    (Path(__file__).parent/'llm_latency_probe.py').read_bytes())
            report['probe']=json.loads(raw.strip().splitlines()[-1])
            status('read-final-timings');report['after']=readings()
            report['completed']=report['probe'].get('completed',False)
            status('complete' if report['completed'] else 'probe-incomplete')
        except Exception as error:
            report.update(completed=False,errorType=type(error).__name__);status('failed')
    print('LLM latency report saved. No rebuild, restart, database or public URL change.',flush=True)
    if not report.get('completed'):raise SystemExit(1)


if __name__=='__main__':main()
