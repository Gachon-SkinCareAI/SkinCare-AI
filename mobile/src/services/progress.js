export const SYMPTOMS = [['UNRECORDED', '미기록'], ['NONE', '없음'], ['PRESENT', '있음'], ['UNKNOWN', '잘 모르겠음']];
export const symptomLabel = value => SYMPTOMS.find(([id]) => id === value)?.[1] || '미기록';
export const recordDate = record => record.capturedOn ? `촬영일 ${record.capturedOn}` : `촬영일 미기록 · 등록 ${String(record.createdAt || '').slice(0, 10)}`;
export const sortRecords = records => [...records].sort((a, b) =>
  (a.capturedOn || String(a.createdAt).slice(0, 10)).localeCompare(b.capturedOn || String(b.createdAt).slice(0, 10))
  || String(a.createdAt).localeCompare(String(b.createdAt)) || a.id.localeCompare(b.id));
