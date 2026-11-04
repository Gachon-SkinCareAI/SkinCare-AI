"""Measure fixed synthetic questionnaire payloads, without storing prompts or replies."""
import json
import math
from pathlib import Path
import time
import urllib.request

from qa import llm_client
from qa.bank_loader import load_bank
from qa.orchestrator import handle_turn
from qa.rules_engine import load_rules

MODEL_DIGEST='359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7'


def response_metadata(value):
    result={}
    for key in ('total_duration','load_duration','prompt_eval_count','prompt_eval_duration','eval_count','eval_duration'):
        item=value.get(key)
        if type(item) in (int,float) and math.isfinite(item) and item>=0:result[key]=item
    if type(value.get('done')) is bool:result['done']=value['done']
    if value.get('done_reason') in ('stop','length'):result['done_reason']=value['done_reason']
    return result


def fixtures():
    """Use the real rule engine to reproduce the smoke-test inputs; no clinical evaluation."""
    bank,rules=load_bank(),load_rules();payloads=[]
    def capture(payload):
        payloads.append(payload)
        return {'action':'ask','question_id':payload['question_ids'][0]}
    result=handle_turn({},bank,rules,capture)
    for answer in ('none','lt_1h'):
        result=handle_turn({'slots':result['slots'],'last_question_id':result['question_id'],
                            'answer':answer,'turn':len(payloads)},bank,rules,capture)
    if [p['question_ids'] for p in payloads]!=[['common.systemic'],['common.elapsed'],['unsure.context']]:
        raise RuntimeError('Synthetic fixture changed')
    return [('unsure-first',payloads[2]),('systemic-control',payloads[0]),('unsure-repeat',payloads[2])]


def memory_snapshot():
    result={}
    for name,path in (('cgroupCurrentBytes','/sys/fs/cgroup/memory.current'),
                      ('cgroupMaxBytes','/sys/fs/cgroup/memory.max')):
        try:
            value=Path(path).read_text().strip()
            if value.isdigit():result[name]=int(value)
        except OSError:pass
    try:
        for line in Path('/proc/meminfo').read_text().splitlines():
            key,value=line.split(':',1)
            if key in ('MemAvailable','SwapTotal','SwapFree'):result[key+'Bytes']=int(value.split()[0])*1024
    except (OSError,ValueError):pass
    return result


def main():
    report={'syntheticInputOnly':True,'clinicalValidation':False,'databaseAccessed':False,
            'productionTimeoutChanged':False,'diagnosticTimeoutSeconds':60,'turns':[]}
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),llm_client._NoRedirect())
    def request(url,body=None,headers=None,timeout=10):
        req=urllib.request.Request(url,data=json.dumps(body).encode('utf-8') if body is not None else None,
                                   headers=headers or {'Content-Type':'application/json'})
        with opener.open(req,timeout=timeout) as response:raw=response.read(1024*1024+1)
        if len(raw)>1024*1024:raise RuntimeError('Response too large')
        return json.loads(raw)
    def loaded():
        models=request('http://llm:11434/api/ps').get('models',[])
        item=next((m for m in models if m.get('name')=='qwen3:4b'),None)
        if not item or item.get('digest')!=MODEL_DIGEST:raise RuntimeError('Expected cached model is not loaded')
        return {key:item[key] for key in ('size','size_vram','context_length') if type(item.get(key)) is int}
    try:
        if not llm_client.configured() or llm_client.provider()!='ollama' or llm_client.model_name()!='qwen3:4b':
            raise RuntimeError('Unexpected local model configuration')
        token=Path('/run/secrets/ai_token').read_text().strip()
        health=request('http://127.0.0.1:8000/api/v1/ai/health',headers={'Authorization':'Bearer '+token})
        if health.get('status')!='ok' or health.get('inflight')!=0:
            report['skipped']='AI is not ready or a user inference is in progress';return report
        report['loadedBefore']=loaded();report['memoryBefore']=memory_snapshot()
        for name,payload in fixtures():
            measurement={'fixture':name};started=time.monotonic()
            def diagnostic_post(body):
                value=request('http://llm:11434/api/chat',body,timeout=60)
                measurement['server']=response_metadata(value)
                if value.get('done') is not True:raise RuntimeError('Incomplete response')
                message=value.get('message') or {}
                measurement['thinkingCharacters']=len(message.get('thinking') or '')
                return message.get('content')
            try:
                result=llm_client.complete(payload,post=diagnostic_post)
                measurement['outputAccepted']=result.get('action')=='ask' and result.get('question_id') in payload['question_ids']
            except Exception as error:
                measurement['errorType']=type(error).__name__
                if error.__cause__:measurement['causeType']=type(error.__cause__).__name__
            measurement['seconds']=round(time.monotonic()-started,3)
            measurement['withinProduction20Seconds']=measurement.get('outputAccepted') is True and measurement['seconds']<=20
            report['turns'].append(measurement)
            if 'errorType' in measurement:break
        report['loadedAfter']=loaded();report['memoryAfter']=memory_snapshot()
        report['completed']=len(report['turns'])==3
    except Exception as error:report['errorType']=type(error).__name__
    return report


if __name__=='__main__':print(json.dumps(main()),flush=True)
