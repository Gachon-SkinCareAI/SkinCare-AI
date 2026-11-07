import React, { useEffect, useRef, useState } from 'react';
import { View, Modal, ScrollView, useWindowDimensions } from 'react-native';
import { Card, Title, T, Button } from '../components/Common';
import { PrivateImage } from '../components/PrivateImage';
import { ErrorNote } from '../components/ConsentControls';
import { RiskBadge } from '../components/RiskIndicator';
import { recordDate, sortRecords, symptomLabel } from '../services/progress';

export function PhotoZoom({ record, api, token, onClose }) {
  const [scale, setScale] = useState(1); const { width, height } = useWindowDimensions();
  return <Modal visible animationType="fade" onRequestClose={onClose}><View style={{ flex: 1, paddingTop: 44, paddingHorizontal: 12, paddingBottom: 24, backgroundColor: '#F5F8F5', gap: 8 }}>
    <T>{recordDate(record)}</T><View style={{ flexDirection: 'row', gap: 8 }}><Button secondary disabled={scale <= 1} onPress={() => setScale(s => s - 1)}>축소</Button><Button secondary disabled={scale >= 3} onPress={() => setScale(s => s + 1)}>확대</Button><Button onPress={onClose}>사진 닫기</Button></View><T>{scale}배 · 확대 후 좌우·위아래로 이동할 수 있어요.</T>
    <ScrollView horizontal><ScrollView><View style={{ width: (width - 24) * scale }}><PrivateImage url={api.base + record.imagePath} token={token} height={Math.max(240, height - 240) * scale} /></View></ScrollView></ScrollView>
  </View></Modal>;
}
export default function ComparisonScreen({ api, token, caseId, initialId }) {
  const [records, setRecords] = useState([]), [left, setLeft] = useState(''), [right, setRight] = useState(initialId);
  const [pair, setPair] = useState(null), [zoom, setZoom] = useState(null), [error, setError] = useState(''), [loading, setLoading] = useState(true), [reload, setReload] = useState(0);
  const sequence = useRef(0);
  useEffect(() => {
    let live = true; setLoading(true); setError('');
    (async () => {
      const all = [];
      for (let page = 0; ; page++) { const batch = await api.list(page, caseId); if (!live) return; all.push(...batch); if (batch.length < 20) break; }
      if (!live) return;
      const sorted = sortRecords(all); setRecords(sorted); setLeft(sorted[0]?.id || ''); setRight(sorted.some(r => r.id === initialId) ? initialId : sorted.at(-1)?.id || ''); setLoading(false);
    })().catch(e => { if (live) { setError(e.message); setLoading(false); } });
    return () => { live = false; };
  }, [api, caseId, initialId, reload]);
  useEffect(() => {
    const seq = ++sequence.current; setPair(null);
    if (!left || !right) return;
    setLoading(true); setError('');
    api.comparison(right, left).then(value => { if (seq === sequence.current) setPair(value); }).catch(e => { if (seq === sequence.current) setError(e.message); }).finally(() => { if (seq === sequence.current) setLoading(false); });
    return () => { sequence.current++; };
  }, [api, left, right, reload]);
  return <><Card><Title>같은 상처의 사진 비교</Title><T>날짜별 사진과 직접 기록한 증상을 나란히 확인해요. 촬영 각도·조명 차이가 있어 사진만으로 회복이나 악화를 판단하지 않아요.</T>
    <ErrorNote message={error}/>{error && <Button onPress={() => setReload(v => v + 1)}>비교 다시 불러오기</Button>}{loading && <T>기록을 불러오는 중이에요…</T>}
    {!loading && !records.length && <T>비교할 사진이 없어요.</T>}
    {[["이전 사진", left, setLeft], ["현재 사진", right, setRight]].map(([label, selected, set]) => <View key={label} style={{ gap: 4 }}><Title>{label} 촬영일 선택</Title><ScrollView horizontal>{records.map((r, index) => <Button key={r.id} secondary={r.id !== selected} disabled={loading} onPress={() => set(r.id)}>{`${label} ${index + 1} · ${recordDate(r)}${r.id === selected ? ' ✓' : ''}`}</Button>)}</ScrollView></View>)}
    {pair?.sameRecord && <T>동일한 사진을 선택했어요. 경과 사진을 추가하면 서로 다른 날짜를 비교할 수 있어요.</T>}
  </Card>{pair && <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>{[["이전", pair.reference], ["현재", pair.current]].map(([label, r]) => <View key={label} style={{ flex: 1, minWidth: 0 }}><Card><Title>{label}</Title><T>{recordDate(r)}</T><PrivateImage url={api.base + r.imagePath} token={token} height={180} label={`${label} 촬영 사진`} /><RiskBadge record={r}/><T>통증 {r.progress?.painScore ?? '미기록'}</T><T>붓기 {symptomLabel(r.progress?.swelling)}</T><T>진물 {symptomLabel(r.progress?.exudate)}</T><T>{r.progress?.note || '메모 없음'}</T><Button secondary onPress={() => setZoom(r)}>{label} 사진 확대</Button></Card></View>)}</View>}
    {zoom && <PhotoZoom record={zoom} api={api} token={token} onClose={() => setZoom(null)}/>}</>;
}
