export const LEVELS = {
  OBSERVATION: {
    title: "관찰",
    stage: 1,
    description: "관찰 안내",
    color: "#17634c",
    background: "#e6f5eb",
  },
  WARNING: {
    title: "경고",
    stage: 2,
    description: "의료기관 확인 권장",
    color: "#8a4900",
    background: "#fff0d9",
  },
  EMERGENCY: {
    title: "응급",
    stage: 3,
    description: "긴급 확인 필요",
    color: "#b91c1c",
    background: "#ffebeb",
  },
  UNKNOWN: {
    title: "판단 불가",
    stage: null,
    description: "현재 정보로 위험도 단계를 정할 수 없어요.",
    color: "#475569",
    background: "#eef2f6",
  },
  NORMAL: {
    title: "정상",
    stage: null,
    legacy: true,
    description: "이전 방식으로 저장된 결과예요.",
    color: "#475569",
    background: "#eef2f6",
  },
};

// UNKNOWN is an assessment status; NORMAL is a legacy stored value. Neither
// belongs to the current three-stage scale. Preserve the original API codes.
export const RISK_STEPS = ["OBSERVATION", "WARNING", "EMERGENCY"];
export function riskLevel(code) {
  return typeof code === "string" && Object.hasOwn(LEVELS, code)
    ? LEVELS[code]
    : LEVELS.UNKNOWN;
}
export function recordRisk(record) {
  if (record.status === "COMPLETED") {
    const level = riskLevel(record.level);
    return {
      ...level,
      label: level.stage
        ? `${level.title} · ${level.stage}단계`
        : level.legacy
          ? `${level.title} · 이전 결과`
          : level.title,
    };
  }
  const states = {
    CREATED: "분석 준비",
    QUESTIONNAIRE: "문진 중",
    RETRY_REQUIRED: "재시도 필요",
  };
  const label = Object.hasOwn(states, record.status)
    ? states[record.status]
    : "확인 중";
  return { ...LEVELS.UNKNOWN, label, stage: null };
}
