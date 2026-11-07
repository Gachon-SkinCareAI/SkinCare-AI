import asyncio
from concurrent.futures import ThreadPoolExecutor
import threading
from types import SimpleNamespace

from app.vision_worker import run_vision
from fastapi import HTTPException
import pytest


@pytest.mark.parametrize('fails',[False,True])
def test_native_trace_timer_is_cancelled_for_each_finished_job(monkeypatch,fails):
    import app.vision_worker as serving
    calls=[]
    monkeypatch.setattr(serving.faulthandler,'dump_traceback_later',lambda *a,**kw:calls.append(('arm',threading.get_ident())))
    monkeypatch.setattr(serving.faulthandler,'cancel_dump_traceback_later',lambda:calls.append(('cancel',threading.get_ident())))
    def native():
        calls.append(('native',threading.get_ident()))
        if fails:raise ValueError('fixture')
        return 7
    with ThreadPoolExecutor(max_workers=1) as executor:
        job=executor.submit(serving._run_native,native,(),True)
        if fails:
            with pytest.raises(ValueError):job.result()
        else:assert job.result()==7
    assert [entry[0] for entry in calls]==['arm','native','cancel']
    assert len({entry[1] for entry in calls})==1
    assert calls[0][1]!=threading.get_ident()


def test_slow_successful_job_has_a_trace_before_http_timeout():
    import os
    import subprocess
    import sys
    code='''
import time
import app.vision_worker as serving
serving._SLOW_JOB_TRACE_SECONDS=.04
def slow():
    private_patient_value='must-not-appear-in-stack'
    time.sleep(.12)
    return 'finished'
assert serving._run_native(slow,(),True)=='finished'
'''
    result=subprocess.run([sys.executable,'-B','-c',code],capture_output=True,timeout=10)
    assert result.returncode==0
    assert b'in slow' in result.stderr
    assert b'must-not-appear-in-stack' not in result.stderr


def test_inference_keeps_event_loop_alive_and_reuses_prepared_thread():
    async def check():
        worker = ThreadPoolExecutor(max_workers=1)
        request = SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(vision_executor=worker)))
        release = threading.Event()
        entered = threading.Event()
        def inference():
            entered.set()
            assert release.wait(3)
            return threading.get_ident()
        try:
            prepared_thread = await run_vision(request, threading.get_ident)
            task = asyncio.create_task(run_vision(request, inference))
            for _ in range(100):
                if entered.is_set():break
                await asyncio.sleep(.01)
            assert entered.is_set()
            # This runs on the HTTP loop while native inference is still busy.
            assert not task.done()
            release.set()
            assert await task == prepared_thread
        finally:
            release.set()
            worker.shutdown()
    asyncio.run(check())


def test_stalled_native_job_times_out_and_rejects_more_work(monkeypatch):
    monkeypatch.delenv('AI_VISION_WATCHDOG_EXIT', raising=False)
    async def check():
        worker = ThreadPoolExecutor(max_workers=1)
        state = SimpleNamespace(vision_executor=worker)
        request = SimpleNamespace(app=SimpleNamespace(state=state))
        release = threading.Event()
        called = []
        try:
            with pytest.raises(HTTPException) as failure:
                await run_vision(request, release.wait, 3, timeout_seconds=.04)
            assert failure.value.status_code == 503
            assert state.vision_failed is True
            with pytest.raises(HTTPException):
                await run_vision(request, lambda: called.append(True))
            assert called == []
        finally:
            release.set()
            worker.shutdown()
    asyncio.run(check())


def test_cancelled_stalled_job_has_a_deadline_too(monkeypatch):
    monkeypatch.delenv('AI_VISION_WATCHDOG_EXIT', raising=False)
    async def check():
        worker = ThreadPoolExecutor(max_workers=1)
        state = SimpleNamespace(vision_executor=worker)
        request = SimpleNamespace(app=SimpleNamespace(state=state))
        entered, release = threading.Event(), threading.Event()
        def native():
            entered.set()
            release.wait(3)
        try:
            task = asyncio.create_task(run_vision(request, native, timeout_seconds=.08))
            while not entered.is_set():
                await asyncio.sleep(.005)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await asyncio.wait_for(task, .5)
            assert state.vision_failed
        finally:
            release.set()
            worker.shutdown()
    asyncio.run(check())


def test_supervised_process_exits_instead_of_leaking_a_stalled_thread():
    import os
    import subprocess
    import sys
    code = '''
import asyncio, threading
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace
from app.vision_worker import run_vision
from fastapi import HTTPException
async def main():
    private_patient_text = 'never-include-this-local-value'
    worker = ThreadPoolExecutor(max_workers=1)
    request = SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(vision_executor=worker)))
    try:
        await run_vision(request, threading.Event().wait, timeout_seconds=.05)
    except HTTPException as error:
        assert error.status_code == 503
    await asyncio.sleep(5)
    raise AssertionError('The stalled process was not terminated')
asyncio.run(main())
'''
    env = dict(os.environ, AI_VISION_WATCHDOG_EXIT='1')
    result = subprocess.run([sys.executable, '-B', '-c', code], env=env,
                            capture_output=True, timeout=10)
    assert result.returncode == 70, result.stderr.decode(errors='replace')
    assert b'threading.py' in result.stderr
    assert b'never-include-this-local-value' not in result.stderr


def test_http_timeout_releases_admission_and_health_reports_recovery(monkeypatch):
    import io
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    from PIL import Image
    from app.routers import predict, health
    monkeypatch.delenv('AI_VISION_WATCHDOG_EXIT', raising=False)
    monkeypatch.setenv('AI_VISION_TIMEOUT_SECONDS', '1')
    monkeypatch.setattr(health, 'load_bank', lambda: {'version':'test'})
    monkeypatch.setattr(health, 'load_cards', lambda: {'version':'test'})
    monkeypatch.setattr(health, 'configured', lambda: False)
    app = FastAPI()
    app.include_router(predict.router)
    app.include_router(health.router)
    release = threading.Event()
    worker = ThreadPoolExecutor(max_workers=1)
    app.state.vision_executor = worker
    app.state.settings = {'max_inflight':1,'service_token':'fixture'}
    app.state.inflight = 0
    app.state.pipeline = SimpleNamespace(ready=True,energy_max=1,
        predict_array=lambda image: release.wait(5))
    encoded = io.BytesIO();Image.new('RGB',(4,4)).save(encoded,format='PNG')
    headers = {'Authorization':'Bearer fixture'}
    try:
        with TestClient(app) as client:
            response = client.post('/ai/predict',files={'file':('fixture.png',encoded.getvalue(),'image/png')},headers=headers)
            assert response.status_code == 503
            assert app.state.inflight == 0
            response = client.get('/ai/health',headers=headers)
            assert response.json()['status'] == 'degraded'
            assert response.json()['vision_recovering'] is True
            response = client.post('/ai/predict',files={'file':('fixture.png',encoded.getvalue(),'image/png')},headers=headers)
            assert response.status_code == 503  # No permanent 'busy' slot.
            assert app.state.inflight == 0
    finally:
        release.set();worker.shutdown()


def test_cancellation_does_not_finish_before_native_inference_stops():
    async def check():
        worker = ThreadPoolExecutor(max_workers=1)
        request = SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(vision_executor=worker)))
        entered, release = threading.Event(), threading.Event()
        def inference():
            entered.set()
            assert release.wait(3)
        try:
            task = asyncio.create_task(run_vision(request, inference))
            for _ in range(100):
                if entered.is_set():break
                await asyncio.sleep(.01)
            assert entered.is_set()
            task.cancel()
            await asyncio.sleep(.02)
            assert not task.done()
            release.set()
            try:await task
            except asyncio.CancelledError:pass
            else:raise AssertionError('Caller cancellation must propagate')
        finally:
            release.set()
            worker.shutdown()
    asyncio.run(check())
