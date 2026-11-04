"""Synthetic single-turn probe; no account, image, patient answer or database access."""
import json
import time
import urllib.error
import urllib.request
from qa import llm_client
from qa.bank_loader import load_bank


def main():
    report={'fixture':'empty-questionnaire-common-systemic','realPatientDataUsed':False,
            'provider':llm_client.provider(),'model':llm_client.model_name()}
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),llm_client._NoRedirect())
    def request(path,body=None,timeout=20):
        start=time.monotonic()
        req=urllib.request.Request('http://llm:11434'+path,
             data=json.dumps(body).encode('utf-8') if body is not None else None,
             headers={'Content-Type':'application/json'})
        try:
            with opener.open(req,timeout=timeout) as response:
                raw=response.read(1024*1024+1)
                if len(raw)>1024*1024:raise ValueError('response_too_large')
                value=json.loads(raw)
            return value,{'ok':True,'seconds':round(time.monotonic()-start,3)}
        except Exception as error:
            result={'ok':False,'seconds':round(time.monotonic()-start,3),'errorType':type(error).__name__}
            if isinstance(error,urllib.error.HTTPError):
                result['httpStatus']=error.code
                result['providerError']=error.read(1500).decode('utf-8',errors='replace')
            return None,result
    try:
        assert llm_client.configured() and report['provider']=='ollama'
        deadline=time.monotonic()+45
        while True:
            _,ready=request('/api/tags',timeout=3)
            if ready['ok']:break
            if time.monotonic()>=deadline:
                report['readiness']=ready
                return report
            time.sleep(1)
        _,report['warmup']=request('/api/generate',{'model':report['model'],'prompt':'','stream':False,
            'keep_alive':'10m','think':False,'options':{'num_ctx':4096}},180)
        running,status=request('/api/ps')
        report['runtimeStatus']=status
        report['loadedModels']=[{key:m.get(key) for key in ('name','size','size_vram','context_length','digest')}
                                for m in (running or {}).get('models',[])]
        if not report['warmup']['ok']:return report
        q=next(q for q in load_bank()['questions'] if q['id']=='common.systemic')
        payload={'slots':{},'user_text':'','question_ids':[q['id']],'questions':[q],'vision_display':None}
        def diagnostic_post(body):
            # Same production payload; allow 60 s ONLY here to measure a slow response.
            value,status=request('/api/chat',body,60)
            status['withinProduction20Seconds']=status['ok'] and status['seconds']<=20
            report['chat']=status
            if value is None:raise RuntimeError('diagnostic_request_failed')
            report['responseMetadata']={key:value.get(key) for key in
                ('done','done_reason','total_duration','load_duration','prompt_eval_count','prompt_eval_duration','eval_count','eval_duration')}
            message=value.get('message') or {}
            report['syntheticResponse']=str(message.get('content',''))[:1500]
            report['thinkingCharacters']=len(message.get('thinking') or '')
            if value.get('done') is not True:raise RuntimeError('llm_invalid')
            return message.get('content')
        output=llm_client.complete(payload,post=diagnostic_post)
        report['outputAccepted']=output.get('action')=='ask' and output.get('question_id')==q['id']
    except Exception as error:
        report['errorType']=type(error).__name__
        report['errorCode']=str(error)[:200]
    return report


if __name__=='__main__':
    print(json.dumps(main(),ensure_ascii=True),flush=True)
