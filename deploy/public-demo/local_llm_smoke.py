"""Run inside the AI container with synthetic software fixtures, never patient data."""
import json
import time
import urllib.request
from qa.bank_loader import load_bank
from qa.rules_engine import load_rules
from qa.orchestrator import handle_turn
from qa.llm_client import complete, configured, model_name, provider, prepare_local
from qa import llm_client


def preload_options():
    # Older immutable local images predate the public runtime patch. Preserve
    # their options when this same probe is used by a local recovery launcher.
    options = getattr(llm_client, 'ollama_options', None)
    return options() if options is not None else {'num_ctx': 4096}


def main():
    assert configured() and provider() == 'ollama' and model_name() == 'qwen3:4b'
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def fetch(path,body=None,timeout=20):
        request=urllib.request.Request('http://llm:11434'+path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={'Content-Type':'application/json'})
        with opener.open(request,timeout=timeout) as response:return json.load(response)
    models=fetch('/api/tags')['models']
    model=next(m for m in models if m['name']==model_name())
    assert model['digest']=='359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7', 'Local model digest changed'
    start=time.monotonic()
    # A cold WSL/GPU load may outlast a normal questionnaire request. Load the
    # cached model explicitly first; only this synthetic startup step gets the
    # larger budget. Production /question timeouts remain unchanged.
    options=preload_options()
    print(json.dumps({'event':'smoke_loading','modelOptions':options}),flush=True)
    preload=fetch('/api/generate',{'model':model_name(),'prompt':'','stream':False,
        'keep_alive':-1,'think':False,'options':options},timeout=600)
    if preload.get('done') is not True:raise RuntimeError('Local model preload did not finish')
    prepare_local()
    warm_seconds=round(time.monotonic()-start,2)
    loaded=fetch('/api/ps')['models']
    print(json.dumps({'event':'smoke_warmup','seconds':warm_seconds,'loaded':[
        {key:model.get(key) for key in ('name','size','size_vram','context_length')} for model in loaded]}),flush=True)
    bank,rules=load_bank(),load_rules()
    measurements=[]
    def actual(payload):
        start=time.monotonic();measurement={'questionIds':payload['question_ids']}
        try:
            out=complete(payload)
            measurement.update(allowed=out.get('question_id') in payload['question_ids'],
                               action=out.get('action'),questionId=out.get('question_id'))
            return out
        except Exception as error:
            measurement.update(errorType=type(error).__name__,errorCode=str(error)[:100],
                               causeType=type(error.__cause__).__name__ if error.__cause__ else None)
            raise
        finally:
            measurement['seconds']=round(time.monotonic()-start,2);measurements.append(measurement)
            print(json.dumps({'event':'smoke_turn',**measurement}),flush=True)
    result=handle_turn({},bank,rules,actual)
    choices={'common.systemic':'none','unsure.context':'abrasion','common.elapsed':'lt_1h','abr.site':'other','abr.dirty':'clean'}
    seen=[]
    for turn in range(1,10):
        if result['action']=='finish':break
        assert result['llm_called'] and not result['overrode_llm'], 'LLM fallback occurred during smoke test'
        qid=result['question_id'];seen.append(qid)
        result=handle_turn({'slots':result['slots'],'last_question_id':qid,'answer':choices.get(qid),'turn':turn},bank,rules,actual)
    assert result['action']=='finish' and seen[-1]=='common.finish_confirm'
    def forbidden(_):raise AssertionError('emergency called LLM')
    emergency=handle_turn({'last_question_id':'common.systemic','answer':'breath'},bank,rules,forbidden)
    assert emergency['level']=='EMERGENCY' and not emergency['llm_called']
    loaded=fetch('/api/ps')['models']
    running=next(m for m in loaded if m['name']==model_name())
    assert running.get('size_vram',0)>0, 'Expected local NVIDIA GPU acceleration'
    print(json.dumps({'passed':True,'model':model_name(),'digest':model['digest'],'modelBytes':model['size'],
        'warmupSeconds':warm_seconds,'gpuBytes':running['size_vram'],'modelOptions':options,
        'turns':measurements,'questionIds':seen,
        'emergencyBypassedLlm':True,'clinicalValidation':False,'realPatientDataUsed':False}))

if __name__=='__main__':main()
