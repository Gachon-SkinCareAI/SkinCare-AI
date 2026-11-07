from app.schemas.qa import AnalyzeRequest
from app.routers.analyze import analyze


def test_initial_symptoms_are_checked_before_incomplete_slots():
    result = analyze(AnalyzeRequest(user_text="숨쉬기 어렵습니다", slots={},
                                    vision={"label": "UNKNOWN", "gate_passed": False}))
    assert result["level"] == "EMERGENCY"
    assert "sys.breath" in result["rule_ids"]


def test_new_symptoms_override_prior_none():
    result = analyze(AnalyzeRequest(user_text="숨쉬기 어렵습니다",
                                    slots={"systemic": ["none"]}))
    assert result["level"] == "EMERGENCY"


def test_incomplete_input_is_not_observation():
    assert analyze(AnalyzeRequest(slots={}))['level'] is None
