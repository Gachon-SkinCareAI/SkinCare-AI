from pydantic import BaseModel, Field, field_validator
from qa.slots import accept


class SlotRequest(BaseModel):
    slots: dict = Field(default_factory=dict)

    @field_validator("slots")
    @classmethod
    def validate_slots(cls, values):
        for key, value in values.items():
            if value is None or value == "" or value == []:
                continue
            try:
                cleaned = accept(key, value)
            except (TypeError, ValueError):
                cleaned = None
            if cleaned is None or (key == "systemic" and
                set([value] if isinstance(value, str) else value) - set(cleaned)):
                raise ValueError(f"Invalid slot: {key}")
        return values


class QuestionRequest(SlotRequest):
    session_id: str | None = None
    bank_version: str | None = None
    user_text: str = ""
    slots: dict = Field(default_factory=dict)
    vision: dict = Field(default_factory=dict)
    turn: int = Field(default=0, ge=0, le=10)
    last_question_id: str | None = None
    answer: str | None = None


class AnalyzeRequest(SlotRequest):
    user_text: str = ""
    session_id: str | None = None
    slots: dict = Field(default_factory=dict)
    vision: dict = Field(default_factory=dict)


class FeedbackRequest(BaseModel):
    request_id: str
    kind: str
    note: str | None = None
    photo_ref: str | None = None


class GuideRequest(BaseModel):
    level: str | None = None
    rule_ids: list[str] = Field(default_factory=list)
    slots: dict = Field(default_factory=dict)
    bank_version: str | None = None
