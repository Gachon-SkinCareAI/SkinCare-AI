from io import BytesIO
from types import SimpleNamespace
import asyncio
import uuid

import pytest
from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
from starlette.datastructures import UploadFile
from app.middleware import RequestIdMiddleware
from app.routers.predict import predict, MAX_BYTES
from qa.bank_loader import load_bank
from qa.rules_engine import load_rules, evaluate
from qa.orchestrator import handle_turn
from qa.slots import merge


def test_new_breathlessness_overrides_prior_none():
    result = handle_turn({"slots": {"scenario": "bruise", "systemic": ["none"]},
                          "user_text": "숨쉬기 어렵습니다"}, load_bank(), load_rules())
    assert result["level"] == "EMERGENCY"
    assert "sys.breath" in result["rule_ids"]


def test_stopped_bleeding_can_become_ongoing():
    result = handle_turn({"slots": {"scenario": "open_wound", "bleeding": "stopped"},
                          "user_text": "피가 계속 나요"}, load_bank(), load_rules())
    assert result["level"] == "EMERGENCY"


def test_emergency_cannot_be_cleared_by_later_text():
    slots, _ = merge({"systemic": ["breath"]}, {"systemic": ["none"]}, from_user=True)
    assert slots["systemic"] == ["breath"]


def test_tenth_turn_with_unknowns_is_not_observation():
    result = handle_turn({"slots": {"scenario": "burn"}, "turn": 10}, load_bank(), load_rules())
    assert result["level"] == "UNKNOWN"


def test_unknown_answers_are_not_observation():
    result = evaluate({"scenario": "burn", "elapsed": "unknown", "cause": "unknown", "site": "unknown"}, load_rules())
    assert result["level"] == "UNKNOWN"


def test_invalid_scenario_cannot_be_observation():
    assert evaluate({"scenario": "bogus"}, load_rules())["level"] is None


def test_llm_cannot_finish_before_pending_safety_questions():
    result = handle_turn({"slots": {"scenario": "burn", "elapsed": "lt_1h", "cause": "flame", "site": "other"},
                          "user_text": "확인"}, load_bank(), load_rules(),
                         llm=lambda _: {"action": "finish", "utterance": "", "slots_delta": {}})
    assert result["action"] == "ask"


def test_request_id_is_generated_internally():
    app = FastAPI()
    app.add_middleware(RequestIdMiddleware)
    @app.get("/")
    def endpoint():
        return {"ok": True}
    with TestClient(app) as client:
        response = client.get("/", headers={"X-Request-Id": "../../overwrite"})
    assert str(uuid.UUID(response.headers["X-Request-Id"])) == response.headers["X-Request-Id"]


def test_predict_never_writes_request_id_as_filename():
    state = SimpleNamespace(inflight=0, settings={"max_inflight": 2},
                            pipeline=SimpleNamespace(ready=True, predict_array=lambda image: {"ok": True}))
    request = SimpleNamespace(app=SimpleNamespace(state=state),
                              state=SimpleNamespace(request_id="../../overwrite"))
    from PIL import Image
    stream = BytesIO()
    Image.new("RGB", (10, 10)).save(stream, format="PNG")
    result = asyncio.run(predict(request, UploadFile(filename="x.png", file=BytesIO(stream.getvalue()))))
    assert result["ok"]
    assert state.inflight == 0


def test_upload_size_is_bounded():
    class Oversized:
        async def read(self, size):
            assert size == MAX_BYTES + 1
            return b"x" * size
    state = SimpleNamespace(inflight=0, settings={"max_inflight": 2})
    request = SimpleNamespace(app=SimpleNamespace(state=state))
    with pytest.raises(HTTPException) as exc:
        asyncio.run(predict(request, Oversized()))
    assert exc.value.status_code == 413
    assert state.inflight == 0


def test_malformed_llm_json_falls_back():
    from qa.llm_client import parse_content
    result = handle_turn({"user_text": "확인"}, load_bank(), load_rules(),
                         llm=lambda _: parse_content("not JSON"))
    assert result["action"] == "ask"
    assert result["overrode_llm"]


@pytest.mark.parametrize("slots", [{"scenario": [] , "site": ["face"]},
                                    {"systemic": 7}, {"scenario": "unsupported"}])
def test_malformed_slots_rejected(slots):
    from pydantic import ValidationError
    from app.schemas.qa import QuestionRequest
    with pytest.raises(ValidationError):
        QuestionRequest(slots=slots)


def test_empty_tenth_turn_also_finishes_unknown():
    result = handle_turn({"turn": 10}, load_bank(), load_rules())
    assert result["action"] == "finish"
    assert result["level"] == "UNKNOWN"


def test_finish_confirmation_cannot_skip_safety_questions():
    result = handle_turn({"slots": {"scenario": "burn", "elapsed": "lt_1h", "cause": "flame", "site": "other"},
                          "last_question_id": "common.finish_confirm"}, load_bank(), load_rules())
    assert result["action"] == "ask"


def test_direct_analysis_requires_warning_rule_inputs():
    result = evaluate({"scenario": "burn", "elapsed": "lt_1h", "cause": "flame", "site": "other"}, load_rules())
    assert result["level"] is None
    assert "systemic" in result["missing_slots"]


def test_choice_questionnaire_finishes_without_llm():
    slots = {}
    question = handle_turn({}, load_bank(), load_rules())
    choices = {"common.systemic": "none", "unsure.context": "abrasion", "common.elapsed": "lt_1h",
               "abr.site": "other", "abr.dirty": "clean"}
    seen = []
    for turn in range(1, 10):
        if question["action"] == "finish":
            break
        qid = question["question_id"]
        seen.append(qid)
        question = handle_turn({"slots": question["slots"], "turn": turn,
                                "last_question_id": qid, "answer": choices.get(qid)}, load_bank(), load_rules())
    assert question["action"] == "finish"
    assert question["level"] == "OBSERVATION"
    assert "abr.dirty" in seen
    assert seen[-1] == "common.finish_confirm"


def test_choice_breath_finishes_immediately():
    result = handle_turn({"last_question_id": "common.systemic", "answer": "breath"}, load_bank(), load_rules())
    assert result["level"] == "EMERGENCY"


def test_choice_outside_question_is_rejected():
    with pytest.raises(ValueError):
        handle_turn({"last_question_id": "common.systemic", "answer": "clean"}, load_bank(), load_rules())
