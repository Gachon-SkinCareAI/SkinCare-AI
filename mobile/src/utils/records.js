// Dates are local calendar dates (YYYY-MM-DD), never UTC-converted timestamps.
export function filterRecords(records, filter) {
  return records.filter(r => filter === '전체' || (filter === '회복 완료' ? r.complete : !r.complete));
}
export function filterTimeline(entries, range) {
  const sorted = [...entries].sort((a,b) => a.date.localeCompare(b.date));
  if (!sorted.length || range === '전체') return sorted;
  const [y,m,d] = sorted.at(-1).date.split('-').map(Number);
  const cutoff = new Date(y, m - 1 - parseInt(range,10), d);
  return sorted.filter(p => { const [a,b,c]=p.date.split('-').map(Number); return new Date(a,b-1,c)>=cutoff; });
}
export function areaChange(entries) {
  const measured=entries.filter(p=>Number.isFinite(p.area));
  if(measured.length<2 || measured[0].area<=0) return null;
  return Math.round((1-measured.at(-1).area/measured[0].area)*1000)/10;
}
