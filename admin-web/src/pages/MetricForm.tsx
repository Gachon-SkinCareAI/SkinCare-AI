import { useState, type FormEvent } from "react";
import { Modal } from "../components";
import { saveAiMetric } from "../services/adminApi";

// 자주 쓰는 지표 키와 화면 문구 (대시보드는 macro_f1, burn_recall을 사용한다)
export const METRIC_PRESETS: [string, string][] = [
  ["macro_f1", "분류 Macro F1"],
  ["macro_auc", "분류 Macro AUC"],
  ["map50", "탐지 mAP@0.5"],
  ["burn_recall", "화상 재현율"],
  ["open_wound_recall", "벌어진 상처 재현율"],
  ["unknown_rate", "UNKNOWN 비율"],
  ["accuracy", "정확도"],
];
const CUSTOM = "__custom__";
const KEY_FORMAT = /^[a-z][a-z0-9_]{1,49}$/;

// 입력창에 채울 값. 수정할 때는 기존 값을 넣어 연다
export type MetricDraft = {
  key: string;
  value: string;
  unit: string;
  description: string;
  // 이미 등록된 지표를 고칠 때는 키를 바꿀 수 없다
  editing: boolean;
};

export function emptyDraft(): MetricDraft {
  return {
    key: METRIC_PRESETS[0][0],
    value: "",
    unit: "%",
    description: "",
    editing: false,
  };
}

// 성능 지표 등록·수정 창. 저장이 끝나면 done을 불러 화면이 최신 값을 다시 읽게 한다
export default function MetricForm({
  initial,
  close,
  done,
}: {
  initial: MetricDraft;
  close: () => void;
  done: (key: string) => Promise<void>;
}) {
  const preset = METRIC_PRESETS.some(([key]) => key === initial.key);
  const [choice, setChoice] = useState(preset ? initial.key : CUSTOM);
  const [custom, setCustom] = useState(preset ? "" : initial.key);
  const [value, setValue] = useState(initial.value);
  const [unit, setUnit] = useState(initial.unit);
  const [description, setDescription] = useState(initial.description);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const key = choice === CUSTOM ? custom.trim() : choice;

  async function submit(e: FormEvent) {
    e.preventDefault();
    const number = Number(value);
    if (!KEY_FORMAT.test(key)) {
      setError(
        "지표 키는 영문 소문자로 시작하고 소문자, 숫자, 밑줄만 쓸 수 있습니다. (2~50자)",
      );
      return;
    }
    if (value.trim() === "" || !Number.isFinite(number) || number < 0) {
      setError("지표 값은 0 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (unit.trim() === "%" && number > 100) {
      setError("단위가 %이면 값은 100 이하여야 합니다.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await saveAiMetric(key, {
        value: number,
        unit: unit.trim(),
        description: description.trim(),
      });
      await done(key);
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "지표 저장에 실패했습니다",
      );
      setSaving(false);
    }
  }

  return (
    <Modal
      title={initial.editing ? "성능 지표 수정" : "성능 지표 등록"}
      close={saving ? () => {} : close}
    >
      <form onSubmit={(e) => void submit(e)} className="metric-form">
        <label className="form-label">
          지표
          <select
            aria-label="지표 종류"
            value={choice}
            disabled={initial.editing || saving}
            onChange={(e) => setChoice(e.target.value)}
          >
            {METRIC_PRESETS.map(([id, label]) => (
              <option key={id} value={id}>
                {label} ({id})
              </option>
            ))}
            <option value={CUSTOM}>직접 입력</option>
          </select>
        </label>
        {choice === CUSTOM && (
          <label className="form-label">
            지표 키
            <input
              aria-label="지표 키"
              value={custom}
              maxLength={50}
              placeholder="예: emergency_recall"
              disabled={initial.editing || saving}
              onChange={(e) => setCustom(e.target.value)}
            />
          </label>
        )}
        <div className="form-row">
          <label className="form-label">
            값
            <input
              aria-label="지표 값"
              type="number"
              step="any"
              min="0"
              value={value}
              data-autofocus
              disabled={saving}
              onChange={(e) => setValue(e.target.value)}
            />
          </label>
          <label className="form-label">
            단위
            <input
              aria-label="단위"
              value={unit}
              maxLength={20}
              placeholder="없으면 비워 두세요"
              disabled={saving}
              onChange={(e) => setUnit(e.target.value)}
            />
          </label>
        </div>
        <label className="form-label">
          설명
          <input
            aria-label="지표 설명"
            value={description}
            maxLength={255}
            placeholder="예: 잠금 test 177장 기준"
            disabled={saving}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <p className="form-hint">
          단위가 %이면 0~100 사이로 입력합니다. 같은 지표를 다시 저장하면
          값이 바뀌고, 이전 값과의 차이가 증감으로 표시됩니다. 입력한 값은
          관리자 감사 이력에 기록됩니다.
        </p>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="modal-actions">
          <button
            type="button"
            className="button secondary"
            onClick={close}
            disabled={saving}
          >
            취소
          </button>
          <button type="submit" className="button primary" disabled={saving}>
            {saving ? "저장 중" : "저장"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
