const LABELS = {
  CREATED: "분석 준비 중",
  QUESTIONNAIRE: "문진 진행 중",
  COMPLETED: "분석 완료",
  RETRY_REQUIRED: "재연결 필요",
};
export const statusLabel = (record) =>
  LABELS[record.status] || "상태 확인 필요";
export function filterRecords(records, filter) {
  if (filter === "분석 완료")
    return records.filter((record) => record.status === "COMPLETED");
  if (filter === "문진 진행 중")
    return records.filter((record) =>
      ["CREATED", "QUESTIONNAIRE", "RETRY_REQUIRED"].includes(record.status),
    );
  return records;
}
