import json
import importlib.util
from pathlib import Path
from io import BytesIO
from unittest.mock import Mock
from unittest.mock import patch
import pytest
from qa import llm_client
from qa.bank_loader import load_bank
from qa.rules_engine import load_rules
from qa.orchestrator import handle_turn


@pytest.fixture
def local(monkeypatch):
    monkeypatch.setenv('LLM_PROVIDER', 'ollama')
    monkeypatch.setenv('LLM_BASE_URL', 'http://llm:11434')
    monkeypatch.setenv('LLM_MODEL', 'qwen3:4b')
    monkeypatch.delenv('LLM_API_KEY', raising=False)


def test_local_request_is_keyless_non_thinking_and_schema_bounded(local):
    def post(body):
        assert body['stream'] is False and body['think'] is False
        assert body['keep_alive'] == -1
        assert body['format']['properties']['question_id']['enum'] == ['common.systemic']
        assert body['options']['num_ctx'] == 4096
        assert body['options']['use_mmap'] is True
        return json.dumps({'action': 'ask', 'question_id': 'common.systemic'})
    assert llm_client.configured()
    assert llm_client.complete({'question_ids': ['common.systemic']}, post)['question_id'] == 'common.systemic'


def test_startup_processes_real_question_without_extending_patient_timeout(local):
    opener = Mock()
    opener.open.side_effect = lambda *args, **kwargs: BytesIO(json.dumps({
        'done': True, 'message': {'content': '{"action":"ask","question_id":"common.systemic"}'}}).encode())
    with patch.object(llm_client.urllib.request, 'build_opener', return_value=opener):
        assert llm_client.prepare_local()['prepared']
        startup = opener.open.call_args
        assert startup.kwargs['timeout'] == 240
        body = json.loads(startup.args[0].data)
        assert body['messages'] and body['keep_alive'] == -1
        assert 'common.systemic' in body['messages'][1]['content']
        llm_client.complete({'question_ids': ['common.systemic']})
        assert opener.open.call_args.kwargs['timeout'] == 20
        assert json.loads(opener.open.call_args.args[0].data)['options'] == body['options']


def test_preload_uses_serving_options_and_supports_immutable_legacy_image(local, monkeypatch):
    script = Path(__file__).resolve().parents[2] / 'deploy/public-demo/local_llm_smoke.py'
    spec = importlib.util.spec_from_file_location('local_llm_probe_fixture', script)
    smoke = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(smoke)
    assert smoke.preload_options() == llm_client.ollama_options()
    assert smoke.preload_options()['use_mmap'] is True
    monkeypatch.delattr(llm_client, 'ollama_options')
    assert smoke.preload_options() == {'num_ctx': 4096}


def test_ollama_loading_options_do_not_leak_to_external_provider(monkeypatch):
    monkeypatch.setenv('LLM_PROVIDER', 'openai-compatible')
    def post(body):
        assert 'options' not in body and 'keep_alive' not in body
        return '{"action":"ask","question_id":"common.systemic"}'
    assert llm_client.complete({'question_ids':['common.systemic']}, post)['action'] == 'ask'


def test_warmup_failure_does_not_count_as_ready(local):
    with patch.object(llm_client, '_post', return_value='{"action":"finish"}'):
        with pytest.raises(RuntimeError, match='llm_warmup_invalid'):
            llm_client.prepare_local()


@pytest.mark.parametrize('url', ['https://api.example.test', 'http://host.docker.internal:11434',
                                 'http://llm:11434/path', 'http://user:pass@llm:11434',
                                 'http://llm:11434?api_key=secret', 'http://llm:bad'])
def test_local_mode_rejects_external_or_ambiguous_urls(local, monkeypatch, url):
    monkeypatch.setenv('LLM_BASE_URL', url)
    assert not llm_client.configured()
    with pytest.raises(RuntimeError): llm_client.complete({'question_ids': ['q']})


def several_candidates():
    return {'slots': {'scenario':'burn','cause':'heat_liquid','site':'other',
                      'elapsed':'lt_1h','systemic':['none']}}


def test_model_does_not_replace_answers_or_wording():
    result = handle_turn(several_candidates(), load_bank(), load_rules(), lambda payload: {
        'action': 'ask', 'question_id': payload['question_ids'][0],
        'slots_delta': {'blister': 'yes'}, 'utterance': 'invented diagnosis'})
    assert result['llm_called'] and not result['overrode_llm']
    assert result['question_id'] == 'burn.blister'
    assert 'blister' not in result['slots']
    assert result['utterance'] != 'invented diagnosis'


def test_single_mandatory_question_does_not_wait_for_llm():
    calls = []
    def llm(payload):
        calls.append(payload)
        return {'action': 'ask', 'question_id': 'common.elapsed'}
    r = handle_turn({'last_question_id': 'unsure.context', 'answer': 'burn'}, load_bank(), load_rules(), llm)
    assert not calls
    assert r['question_id'] == 'common.systemic' and not r['llm_called']
    assert r['slots']['scenario'] == 'burn'


@pytest.mark.parametrize('output', [[], {'action': 'finish'}, {'action': 'ask', 'question_id': []},
                                  {'action': 'ask', 'question_id': 'common.systemic', 'utterance': {}}])
def test_malformed_model_outputs_do_not_break_questionnaire(output):
    r = handle_turn(several_candidates(), load_bank(), load_rules(), lambda _: output)
    assert r['action'] == 'ask' and r['question_id'] == 'burn.blister'
    assert r['llm_called'] and r['overrode_llm']


def test_timeout_and_busy_fall_back_without_losing_choice(local):
    with patch.object(llm_client, '_post', side_effect=RuntimeError('llm_unavailable')):
        body=several_candidates();body['slots'].pop('systemic')
        body.update(last_question_id='common.systemic',answer='none')
        r = handle_turn(body,
                        load_bank(), load_rules(), llm_client.complete)
    assert r['slots']['systemic'] == ['none']
    assert r['overrode_llm'] and r['llm_called']
    with llm_client._SLOTS:
        with pytest.raises(RuntimeError, match='llm_busy'):
            llm_client.complete({'question_ids': ['common.systemic']})


def test_model_still_selects_between_equally_ranked_questions():
    calls=[]
    def select(payload):
        calls.append(payload['question_ids'])
        return {'action':'ask','question_id':'burn.stuck'}
    result=handle_turn(several_candidates(),load_bank(),load_rules(),select)
    assert calls==[['burn.blister','burn.stuck']]
    assert result['question_id']=='burn.stuck' and result['llm_called']


def test_abrasion_flow_completes_without_network_when_every_choice_is_fixed():
    def forbidden(_):raise AssertionError('Single question must not call the model')
    bank,rules=load_bank(),load_rules()
    vision={'gate_passed':False,'label':'UNKNOWN'}
    body={'vision':vision};seen=[]
    answers={'common.systemic':'none','common.elapsed':'lt_1h','unsure.context':'abrasion',
             'abr.site':'other','abr.dirty':'clean'}
    for turn in range(8):
        result=handle_turn(body,bank,rules,forbidden)
        assert not result['llm_called']
        if result['action']=='finish':break
        qid=result['question_id'];seen.append(qid)
        body={'vision':vision,'slots':result['slots'],'last_question_id':qid,'answer':answers.get(qid),
              'user_text':'','turn':turn+1}
    assert seen==['common.systemic','common.elapsed','unsure.context','abr.site','abr.dirty','common.finish_confirm']
    assert result['action']=='finish' and result['level']=='UNKNOWN'


def test_emergency_never_waits_for_model():
    def forbidden(_): raise AssertionError('must not call LLM')
    r = handle_turn({'last_question_id': 'common.systemic', 'answer': 'breath'},
                    load_bank(), load_rules(), forbidden)
    assert r['level'] == 'EMERGENCY' and not r['llm_called']
