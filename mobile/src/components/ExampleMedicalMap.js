import React, { useState } from 'react';
import { View, Pressable } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';
import { Card, Title, T, Button } from './Common';
import { Field } from './ConsentControls';
const PLACES = [
  { id: 'example-h1', kind: '병원', name: '예시 병원 A', x: 22, y: 28 },
  { id: 'example-h2', kind: '병원', name: '예시 병원 B', x: 65, y: 65 },
  { id: 'example-p1', kind: '약국', name: '예시 약국 A', x: 55, y: 20 },
  { id: 'example-p2', kind: '약국', name: '예시 약국 B', x: 20, y: 72 },
];
export default function ExampleMedicalMap() {
  const [query, setQuery] = useState(''), [kind, setKind] = useState('전체'), [selected, setSelected] = useState(null);
  const places = PLACES.filter(p => (kind === '전체' || p.kind === kind) && p.name.includes(query.trim()));
  return <Card><Title>예시 지도 체험</Title><T>가상 기관과 도식 지도예요. 실제 위치·진료기관·영업 정보가 아니며 GPS를 사용하지 않아요.</T>
    <Field label="예시 기관 검색" value={query} onChangeText={setQuery} maxLength={80}/><View style={{ flexDirection: 'row', gap: 8 }}>{['전체','병원','약국'].map(k => <Button key={k} secondary={k !== kind} onPress={() => {setKind(k);setSelected(null);}}>{`예시 ${k}`}</Button>)}</View>
    <View accessibilityLabel="가상 기관 예시 지도" style={{ height: 240, backgroundColor: '#E8F0EB', borderRadius: 14, overflow: 'hidden' }}><Svg width="100%" height="100%" viewBox="0 0 300 240" preserveAspectRatio="none"><Rect x="175" y="8" width="100" height="90" rx="12" fill="#C9DFCC"/><Path d="M0 120 H300 M120 0 V240 M0 205 L300 30" stroke="white" strokeWidth="18"/></Svg>{places.map(p => <Pressable key={p.id} accessibilityRole="button" accessibilityLabel={`${p.name} 지도 표시`} onPress={() => setSelected(p.id)} style={{ position: 'absolute', left: `${p.x}%`, top: `${p.y}%`, padding: 8, borderRadius: 12, backgroundColor: selected === p.id ? '#0D2B6B' : 'white', minHeight: 44 }}><T style={{ color: selected === p.id ? 'white' : '#0D2B6B' }}>{p.kind === '병원' ? '+' : '약'} {p.name.slice(-1)}</T></Pressable>)}</View>
    {places.map(p => <Button key={p.id} secondary={selected !== p.id} onPress={() => setSelected(p.id)}>{p.name}{selected === p.id ? ' · 선택됨' : ''}</Button>)}{!places.length && <T>해당하는 예시 기관이 없어요.</T>}
  </Card>;
}
