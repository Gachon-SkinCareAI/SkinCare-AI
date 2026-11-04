"""Synthetic latency probe inside the existing AI container; no patient records."""
import io
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request

import cv2
import numpy as np
from PIL import Image
import torch


def optional_text(path):
    try:return Path(path).read_text().strip()
    except OSError:return None


def main():
    token=Path('/run/secrets/ai_token').read_text().strip()
    report={'syntheticInputOnly':True,'clinicalAccuracyMeasured':False,'databaseAccessed':False,
        'runtime':{'torch':torch.__version__,'cudaAvailable':torch.cuda.is_available(),
            'torchThreadsInProbe':torch.get_num_threads(),'interopThreadsInProbe':torch.get_num_interop_threads(),
            'opencvThreadsInProbe':cv2.getNumThreads(),'logicalCpuCount':os.cpu_count(),
            'cpuQuota':optional_text('/sys/fs/cgroup/cpu.max'),
            'memoryMaxBytes':optional_text('/sys/fs/cgroup/memory.max'),
            'memoryCurrentBytes':optional_text('/sys/fs/cgroup/memory.current')},'requests':[]}

    def request(path,payload=None,content_type='application/json'):
        headers={'Authorization':'Bearer '+token}
        if payload is not None:headers['Content-Type']=content_type
        req=urllib.request.Request('http://127.0.0.1:8000/api/v1/ai/'+path,data=payload,headers=headers)
        start=time.perf_counter();measurement={'route':path}
        result=None
        try:
            with urllib.request.urlopen(req,timeout=65) as response:
                measurement['status']=response.status;result=json.load(response)
        except urllib.error.HTTPError as error:measurement.update(status=error.code,errorType='HTTPError')
        except (OSError,ValueError) as error:measurement['errorType']=type(error).__name__
        measurement['seconds']=round(time.perf_counter()-start,3)
        return result,measurement

    health,_=request('health')
    if not health or health.get('status')!='ok' or health.get('inflight')!=0:
        report['skipped']='AI is not ready or a user inference is in progress'
        print(json.dumps(report));return

    # Noise is a software input only. No predicted label/probability/image is recorded.
    pixels=np.random.default_rng(20261103).integers(40,210,(1200,1600,3),dtype=np.uint8)
    image=io.BytesIO();Image.fromarray(pixels).save(image,format='JPEG',quality=88)
    boundary='skincare-synthetic-latency-check'
    content=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="synthetic.jpg"\r\n'
              'Content-Type: image/jpeg\r\n\r\n').encode()+image.getvalue()+('\r\n--'+boundary+'--\r\n').encode())
    report['syntheticJpegBytes']=len(image.getvalue())
    for _ in range(2):
        result,measurement=request('predict',content,'multipart/form-data; boundary='+boundary)
        if result:
            measurement['qualityPassed']=result.get('quality',{}).get('ok')
            measurement['classifierWasExecuted']=bool((result.get('classification') or {}).get('probabilities'))
        report['requests'].append(measurement)
        if not result:break
    for _ in range(2):
        body={'session_id':'synthetic-latency-check','turn':0,'slots':{},'user_text':'',
              'vision':{'gate_passed':False,'label':'UNKNOWN'}}
        result,measurement=request('question',json.dumps(body).encode())
        if result:
            measurement['llmCalled']=result.get('llm_called')
            measurement['usedFallback']=result.get('overrode_llm')
        report['requests'].append(measurement)
        if not result:break

    # A noise image usually bypasses ViT. Measure that unchanged component separately,
    # only when the existing container and Docker VM have enough spare memory.
    memory_max=optional_text('/sys/fs/cgroup/memory.max')
    memory_current=optional_text('/sys/fs/cgroup/memory.current')
    meminfo=dict(line.split(':',1) for line in Path('/proc/meminfo').read_text().splitlines())
    spare_vm=int(meminfo['MemAvailable'].split()[0])*1024
    spare_container=int(memory_max)-int(memory_current) if memory_max and memory_max.isdigit() else 0
    report['classifierProbeMemoryGuard']={'containerSpareBytes':spare_container,'vmAvailableBytes':spare_vm}
    if min(spare_vm,spare_container)>=1600*1024**2:
        from vision.classify import WoundClassifier
        start=time.perf_counter()
        classifier=WoundClassifier(Path('/models/classifier.pt'),torch.device('cpu'))
        report['classifierLoadSeconds']=round(time.perf_counter()-start,3)
        report['classifierCpuTrials']=[];reference=None
        for threads in (2,4):
            torch.set_num_threads(threads)
            for attempt in range(2):
                start=time.perf_counter();logits=classifier.logits(pixels)
                if reference is None:reference=logits.copy()
                report['classifierCpuTrials'].append({'threads':threads,'attempt':attempt+1,
                    'seconds':round(time.perf_counter()-start,3),'finite':bool(np.isfinite(logits).all()),
                    'maxAbsDeltaFromFirst':float(np.max(np.abs(logits-reference))),
                    'argmaxMatchesFirst':bool(np.argmax(logits)==np.argmax(reference))})
    else:report['classifierCpuTrialsSkipped']='insufficient spare memory; live service was not stopped'
    print(json.dumps(report),flush=True)


if __name__=='__main__':main()
