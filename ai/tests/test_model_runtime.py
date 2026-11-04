import json
import asyncio
import logging
from types import SimpleNamespace

import numpy as np
import pytest
import torch
from torch import nn

import eval_classify
from train_classify import CLASSES
from vision.pipeline import VisionPipeline


class TinyVit(nn.Module):
    def __init__(self):
        super().__init__()
        self.heads = nn.Module()
        self.heads.head = nn.Linear(4, 5)

    def forward(self, value):
        return self.heads.head(value)


def test_checkpoint_values_and_cpu_outputs_are_preserved_without_random_initialization(tmp_path, monkeypatch):
    reference = TinyVit().eval()
    checkpoint = tmp_path / 'fixture.pt'
    torch.save({'classes': CLASSES, 'model': reference.state_dict()}, checkpoint)
    devices = []
    def factory(weights):
        model = TinyVit()
        devices.append(next(model.parameters()).device.type)
        return model
    monkeypatch.setattr(eval_classify, 'vit_b_16', factory)
    actual = eval_classify.load_model(checkpoint, torch.device('cpu'))
    assert devices == ['meta']
    assert not actual.training
    assert all(not value.is_meta for value in actual.parameters())
    for name, value in reference.state_dict().items():
        assert torch.equal(value, actual.state_dict()[name])
    sample = torch.arange(12, dtype=torch.float32).reshape(3, 4)
    assert torch.equal(reference(sample), actual(sample))


def test_half_checkpoint_retains_previous_float32_serving_behavior(tmp_path, monkeypatch):
    reference = TinyVit().half().eval()
    checkpoint = tmp_path / 'fixture.pt'
    torch.save({'classes': CLASSES, 'model': reference.state_dict()}, checkpoint)
    monkeypatch.setattr(eval_classify, 'vit_b_16', lambda weights: TinyVit())
    actual = eval_classify.load_model(checkpoint, torch.device('cpu'))
    assert all(value.dtype == torch.float32 for value in actual.parameters())
    for name, value in reference.state_dict().items():
        assert torch.equal(value.float(), actual.state_dict()[name])


def test_incomplete_or_wrong_class_checkpoint_still_fails(tmp_path, monkeypatch):
    checkpoint = tmp_path / 'fixture.pt'
    monkeypatch.setattr(eval_classify, 'vit_b_16', lambda weights: TinyVit())
    torch.save({'classes': CLASSES[::-1], 'model': TinyVit().state_dict()}, checkpoint)
    with pytest.raises(ValueError):eval_classify.load_model(checkpoint, torch.device('cpu'))
    torch.save({'classes': CLASSES, 'model': {}}, checkpoint)
    with pytest.raises(RuntimeError):eval_classify.load_model(checkpoint, torch.device('cpu'))


def test_warmup_exercises_classifier_even_when_detector_has_no_box():
    pipeline = VisionPipeline()
    shapes=[];classified=[]
    pipeline.detector = SimpleNamespace(predict=lambda image: shapes.append(image.shape) or [])
    pipeline.classifier = SimpleNamespace(probabilities=lambda image: classified.append(image.shape) or (np.ones(5)/5, -1.))
    result=pipeline.warmup()
    assert shapes == [(640,640,3),(480,640,3),(640,480,3)]
    assert classified == [(640,640,3)]
    assert result['synthetic_input_only'] is True


def test_warmup_rejects_invalid_classifier_output():
    pipeline=VisionPipeline()
    pipeline.detector=SimpleNamespace(predict=lambda image: [])
    pipeline.classifier=SimpleNamespace(probabilities=lambda image: (np.full(5,np.nan),-1.))
    with pytest.raises(RuntimeError):pipeline.warmup()


def test_timing_logs_do_not_change_gate_or_include_image_and_prediction(caplog):
    pipeline=VisionPipeline(energy_max=5.)
    pipeline.detector=SimpleNamespace(predict=lambda image: [((.1,.1,.5,.5),.9)])
    pipeline.classifier=SimpleNamespace(probabilities=lambda image: (np.array([.9,.025,.025,.025,.025]),-1.))
    sample=np.random.default_rng(1).integers(40,210,(320,320,3),dtype=np.uint8)
    with caplog.at_level(logging.INFO,logger='skincare.performance'):
        result=pipeline.predict_array(sample)
    assert result['classification']['label']=='abrasion'
    assert 'total_ms' not in result and 'timings' not in result
    record=json.loads(caplog.records[-1].message)
    assert set(record)=={'event','device','total_ms','quality_ms','detector_ms','classifier_ms'}
    assert 'abrasion' not in caplog.text


def test_failed_quality_still_skips_both_models():
    pipeline=VisionPipeline()
    def unexpected(*args):raise AssertionError('Bad image must not invoke a model')
    pipeline.detector=SimpleNamespace(predict=unexpected)
    pipeline.classifier=SimpleNamespace(probabilities=unexpected)
    result=pipeline.predict_array(np.zeros((100,100,3),dtype=np.uint8))
    assert result['quality']['ok'] is False
    assert result['classification'] is None


@pytest.mark.parametrize('warmup_fails',[False,True])
def test_serving_readiness_waits_for_warmup_and_fails_closed(monkeypatch,tmp_path,warmup_fails):
    import app.main as serving
    events=[]
    class PreparedPipeline:
        def __init__(self,config):
            self.detector=None;self.classifier=None;self.device=torch.device('cpu')
        def load(self,*args):
            events.append('load');self.detector=object();self.classifier=object()
        def warmup(self):
            events.append('warmup')
            if warmup_fails:raise RuntimeError('private internal detail')
        @property
        def ready(self):return self.detector is not None and self.classifier is not None
    monkeypatch.setattr(serving,'VisionPipeline',PreparedPipeline)
    monkeypatch.setenv('AI_VISION_WARMUP','1')
    monkeypatch.setenv('AI_ENERGY_PATH',str(tmp_path/'absent.json'))
    async def check():
        app=SimpleNamespace(state=SimpleNamespace())
        async with serving.lifespan(app):
            assert events==['load','warmup']
            assert app.state.pipeline.ready is (not warmup_fails)
    asyncio.run(check())
