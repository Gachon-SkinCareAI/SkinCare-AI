"""한 턴의 다음 질문. 응급 규칙이 성립하면 언어 모델을 부르지 않는다."""

from __future__ import annotations

from qa.filter import reject_utterance
from qa.rules_engine import evaluate, missing_slots
from qa.slots import SLOT_VALUES, empty, merge, read_user_text

_RANK = {
    "common.systemic": 0,
    "burn.cause": 1,
    "open.bleeding": 1,
    "common.elapsed": 2,
    "burn.site": 3,
    "abr.site": 3,
    "bruise.site": 3,
}


def candidates(slots: dict, bank: dict, vision: dict | None = None, rules: dict | None = None) -> list[str]:
    slots = slots or {}
    vision = vision or {}
    by_id = {item["id"]: item for item in bank.get("questions", []) if item.get("id")}
    found = []
    scenario = slots.get("scenario")
    emergency = False
    if rules is not None:
        emergency = evaluate(slots, rules, vision)["level"] == "EMERGENCY"

    def add(question_id: str, ready: bool) -> None:
        if ready and question_id in by_id:
            found.append(question_id)

    add("unsure.context", empty(scenario))
    add("common.systemic", empty(slots.get("systemic")))
    add("common.elapsed", empty(slots.get("elapsed")))
    add("burn.cause", scenario == "burn" and empty(slots.get("cause")))
    add("burn.site", scenario == "burn" and slots.get("cause") not in {"chemical", "electric"} and empty(slots.get("site")))
    add("burn.blister", scenario == "burn" and not emergency and empty(slots.get("blister")))
    add("burn.stuck", scenario == "burn" and slots.get("elapsed") == "lt_1h" and empty(slots.get("stuck_clothes")))
    add("open.bleeding", scenario == "open_wound" and empty(slots.get("bleeding")))
    bleeding_closed = slots.get("bleeding") not in (None, "", "ongoing")
    add("open.gape", scenario == "open_wound" and bleeding_closed and empty(slots.get("wound_open")))
    add("open.foreign", scenario == "open_wound" and bleeding_closed and empty(slots.get("foreign_body")))
    add("open.dirty", scenario == "open_wound" and bleeding_closed and empty(slots.get("dirty")))
    add("open.tetanus_memory", slots.get("dirty") in {"soil", "rust", "animal"} and empty(slots.get("tetanus_memory")))
    add("abr.site", scenario == "abrasion" and empty(slots.get("site")))
    add("abr.dirty", scenario == "abrasion" and empty(slots.get("dirty")))
    add("bruise.site", scenario == "bruise" and empty(slots.get("site")))
    add("bruise.spread", scenario == "bruise" and empty(slots.get("red_spread")))
    add("late.spread", slots.get("elapsed") == "gt_72h" and empty(slots.get("red_spread")))
    add("late.pus", slots.get("elapsed") == "gt_72h" and empty(slots.get("pus")))
    add("late.pain", slots.get("elapsed") == "gt_72h" and empty(slots.get("pain_trend")))
    ready_to_close = not missing_slots(slots) and not emergency and not found
    add("common.finish_confirm", ready_to_close)
    return sorted(set(found), key=lambda item: (_RANK.get(item, 4), item))


def _question(bank: dict, question_id: str) -> dict:
    for item in bank.get("questions", []):
        if item.get("id") == question_id:
            return item
    return {}


def _ask(question: dict, slots: dict, delta: dict, *, overrode: bool, called: bool) -> dict:
    return {
        "action": "ask",
        "question_id": question.get("id"),
        "utterance": question.get("prompt"),
        "slots": slots,
        "slots_delta": delta,
        "level": None,
        "rule_ids": [],
        "overrode_llm": overrode,
        "llm_called": called,
        "choices": question.get("choices") or [],
    }


def _finish(slots: dict, delta: dict, decision: dict, *, called: bool) -> dict:
    return {
        "action": "finish",
        "question_id": None,
        "utterance": None,
        "slots": slots,
        "slots_delta": delta,
        "level": decision["level"],
        "rule_ids": decision["rule_ids"],
        "overrode_llm": False,
        "llm_called": called,
        "choices": [],
        "missing_slots": decision["missing_slots"],
    }


def _fill_unknown(slots: dict) -> dict:
    filled = dict(slots)
    for key in SLOT_VALUES:
        if empty(filled.get(key)):
            filled[key] = ["unknown"] if key == "systemic" else ("unsure" if key == "scenario" else "unknown")
    return filled


def handle_turn(body: dict, bank: dict, rules: dict, llm=None) -> dict:
    slots = dict(body.get("slots") or {})
    vision = dict(body.get("vision") or {})
    text = body.get("user_text") or ""
    selected = body.get("answer")
    delta = {}
    if selected is not None:
        question = _question(bank, body.get("last_question_id"))
        choices = {choice.get("value") for choice in question.get("choices", [])}
        if not question or selected not in choices or not question.get("asks_slot"):
            raise ValueError("invalid_answer")
        slots, delta = merge(slots, {question["asks_slot"]: selected}, from_user=True)
    heard, text_delta = merge(slots, read_user_text(text), from_user=True)
    delta.update(text_delta)
    decision = evaluate(heard, rules, vision)
    if decision["level"] == "EMERGENCY":
        return _finish(heard, delta, decision, called=False)

    if int(body.get("turn") or 0) >= 10:
        closed = _fill_unknown(heard)
        final = evaluate(closed, rules, vision)
        if final["level"] is None:
            final = {**final, "level": "UNKNOWN", "rule_ids": ["answers.incomplete"]}
        return _finish(closed, delta, final, called=False)

    options = candidates(heard, bank, vision, rules)
    if (body.get("last_question_id") == "common.finish_confirm" and not missing_slots(heard)
            and not any(qid != "common.finish_confirm" for qid in options)):
        return _finish(heard, delta, evaluate(heard, rules, vision), called=False)
    if not options:
        return _finish(heard, delta, decision, called=False)

    if llm is None:
        return _ask(_question(bank, options[0]), heard, delta, overrode=False, called=False)

    # Mandatory risk questions precede ordinary questions, even with an LLM.
    rank = _RANK.get(options[0], 4)
    options = [qid for qid in options if _RANK.get(qid, 4) == rank]

    called = llm is not None
    output = {}
    if called:
        try:
            output = llm(
                {
                    "slots": heard,
                    "user_text": text,
                    "question_ids": options,
                    "questions": [_question(bank, item) for item in options],
                    "vision_display": vision.get("display"),
                }
            ) or {}
            if not isinstance(output, dict):
                output = {}
        except (RuntimeError, ValueError, TypeError):
            output = {}
    chosen = output.get("question_id")
    utterance = output.get("utterance") or ""
    invalid = (
        not called
        or output.get("action") != "ask"
        or not isinstance(chosen, str) or chosen not in options
        or not isinstance(utterance, str) or bool(reject_utterance(utterance))
    )
    if invalid:
        return _ask(_question(bank, options[0]), heard, delta, overrode=True, called=called)

    # Generated slots/wording cannot become clinical facts or advice. Explicit
    # choices and the existing deterministic text parser remain authoritative.
    return _ask(_question(bank, chosen), heard, delta, overrode=False, called=True)
