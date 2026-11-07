import importlib.util
import io
import json
from pathlib import Path
from types import SimpleNamespace
from urllib.parse import urlsplit

from fastapi import FastAPI
from fastapi.testclient import TestClient
from app.routers import question


def test_http_probe_uses_real_question_routes_and_never_calls_model_for_fixed_flow(monkeypatch,capsys):
    path=Path(__file__).resolve().parents[2]/'deploy/public-demo/questionnaire_startup_probe.py'
    spec=importlib.util.spec_from_file_location('question_probe_fixture',path)
    probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)
    app=FastAPI();app.state.settings={'service_token':'fixture-token'}
    app.include_router(question.router,prefix='/api/v1')
    monkeypatch.setattr(question,'configured',lambda:True)
    def forbidden(_):raise AssertionError('Fixed workflow must not wait for model selection')
    monkeypatch.setattr(question,'_llm',forbidden)
    read_text=Path.read_text
    monkeypatch.setattr(Path,'read_text',lambda self,*a,**k:'fixture-token' if str(self).replace('\\','/')=='/run/secrets/ai_token' else read_text(self,*a,**k))
    with TestClient(app) as client:
        def open(request,timeout):
            response=client.post(urlsplit(request.full_url).path,content=request.data,headers=dict(request.header_items()))
            result=io.BytesIO(response.content);result.status=response.status_code
            return result
        monkeypatch.setattr(probe.urllib.request,'build_opener',lambda *a:SimpleNamespace(open=open))
        probe.main()
    report=json.loads(capsys.readouterr().out)
    assert report['passed'] and len(report['checks'])==7
    assert not report['patientRecordsCreated'] and not report['llmChoiceExercised']
