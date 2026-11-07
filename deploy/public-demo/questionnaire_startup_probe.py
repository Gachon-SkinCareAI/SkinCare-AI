"""Validate the fixed questionnaire sequence without creating database records."""
import json
from pathlib import Path
import time
import urllib.request


def main():
    token=Path('/run/secrets/ai_token').read_text().strip()
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    vision={'gate_passed':False,'label':'UNKNOWN'}
    body={'slots':{},'vision':vision};seen=[];checks=[]
    answers={'common.systemic':'none','common.elapsed':'lt_1h','unsure.context':'abrasion',
             'abr.site':'other','abr.dirty':'clean'}
    for turn in range(8):
        request=urllib.request.Request('http://127.0.0.1:8000/api/v1/ai/question',
            data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
        started=time.monotonic()
        with opener.open(request,timeout=5) as response:
            result=json.load(response)
            if response.status!=200 or result.get('llm_called') is not False:
                raise RuntimeError('Fixed questionnaire unexpectedly waits for a model choice')
        seconds=round(time.monotonic()-started,3)
        if seconds>3:raise RuntimeError('Fixed questionnaire exceeds the response budget')
        checks.append({'status':200,'seconds':seconds})
        if result.get('action')=='finish':break
        qid=result.get('question_id');seen.append(qid)
        body={'vision':vision,'slots':result['slots'],'last_question_id':qid,
              'answer':answers.get(qid),'turn':turn+1,'user_text':''}
    expected=['common.systemic','common.elapsed','unsure.context','abr.site','abr.dirty','common.finish_confirm']
    if seen!=expected or result.get('action')!='finish' or result.get('level')!='UNKNOWN':
        raise RuntimeError('Fixed questionnaire sequence or conservative result changed')
    print(json.dumps({'passed':True,'patientRecordsCreated':False,'fictionalAnswers':True,
        'llmChoiceExercised':False,'checks':checks}),flush=True)


if __name__=='__main__':main()
