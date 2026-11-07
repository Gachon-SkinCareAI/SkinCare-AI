import importlib.util
import io
import json
from pathlib import Path
from types import SimpleNamespace

from PIL import Image
import pytest

path=Path(__file__).resolve().parents[2]/'deploy/public-demo/vision_startup_probe.py'
spec=importlib.util.spec_from_file_location('live_vision_probe',path)
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)


@pytest.mark.parametrize('stdin',[False,True])
def test_validation_photo_reaches_http_unchanged_and_checks_classifier(tmp_path,monkeypatch,capsys,stdin):
    image=io.BytesIO();Image.new('RGB',(1987,1154),(180,100,70)).save(image,format='JPEG')
    raw=image.getvalue();photo=tmp_path/'sample.jpg';photo.write_bytes(raw)
    read_text=Path.read_text
    monkeypatch.setattr(Path,'read_text',lambda self,*a,**k:'fixture-token' if str(self).replace('\\','/')=='/run/secrets/ai_token' else read_text(self,*a,**k))
    bodies=[]
    def open(request,timeout):
        bodies.append(request.data)
        response=io.StringIO(json.dumps({'quality':{'ok':True},'classification':{'energy':-1}}))
        response.status=200
        return response
    monkeypatch.setattr(probe.urllib.request,'build_opener',lambda *a:SimpleNamespace(open=open))
    if stdin:
        monkeypatch.setattr(probe.sys,'stdin',SimpleNamespace(buffer=io.BytesIO(raw)))
        def forbidden_write(*a,**kw):raise AssertionError('The photo must remain in memory')
        monkeypatch.setattr(Path,'write_bytes',forbidden_write)
        probe.main(['--stdin-photo'])
    else:probe.main(['--photo',str(photo)])
    assert len(bodies)==1
    assert bodies[0].split(b'\r\n\r\n',1)[1].rsplit(b'\r\n--',1)[0]==raw
    result=json.loads(capsys.readouterr().out)
    assert result['passed'] and result['validationPhoto'] and not result['syntheticInputOnly']
    assert result['checks'][0]['classifierReached']
    assert result['patientRecordsCreated'] is False


def test_invalid_photo_is_not_silently_replaced_with_synthetic_pixels(tmp_path):
    photo=tmp_path/'not-a-photo.jpg';photo.write_bytes(b'wrong')
    with pytest.raises(RuntimeError):list(probe.inputs(photo))


@pytest.mark.parametrize('raw',[b'',b'wrong',b'\xff\xd8'+b'x'*(8*1024*1024)],
                         ids=['empty','invalid','oversized'])
def test_bad_stdin_photo_cannot_fall_back_to_synthetic(raw):
    with pytest.raises(RuntimeError):list(probe.inputs(photo_bytes=raw))


def test_real_python_command_preserves_binary_stdin_without_writing_files(tmp_path):
    import subprocess
    import sys
    image=io.BytesIO();Image.new('RGB',(321,257),(180,100,70)).save(image,format='JPEG')
    raw=image.getvalue()
    code='''
import hashlib, io, json, pathlib, sys, urllib.request
from types import SimpleNamespace
original_read=pathlib.Path.read_text
pathlib.Path.read_text=lambda self,*a,**k: 'fixture' if str(self).replace('\\\\','/')=='/run/secrets/ai_token' else original_read(self,*a,**k)
def no_write(*a,**k):raise AssertionError('No filesystem writes allowed')
pathlib.Path.write_bytes=no_write
pathlib.Path.write_text=no_write
def send(request,timeout):
    raw=request.data.split(b'\\r\\n\\r\\n',1)[1].rsplit(b'\\r\\n--',1)[0]
    print(json.dumps({'receivedSha256':hashlib.sha256(raw).hexdigest()}))
    result=io.StringIO(json.dumps({'quality':{'ok':True},'classification':{'energy':-1}}))
    result.status=200
    return result
urllib.request.build_opener=lambda *a:SimpleNamespace(open=send)
'''+path.read_text(encoding='utf-8')
    result=subprocess.run([sys.executable,'-B','-c',code,'--stdin-photo'],input=raw,
                          capture_output=True,cwd=tmp_path,timeout=15)
    assert result.returncode==0,result.stderr.decode(errors='replace')
    lines=result.stdout.decode().splitlines()
    import hashlib
    assert json.loads(lines[0])['receivedSha256']==hashlib.sha256(raw).hexdigest()
    assert json.loads(lines[-1])['validationPhoto'] is True
    assert list(tmp_path.iterdir())==[]
