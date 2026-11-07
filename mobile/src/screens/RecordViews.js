import React, { useState } from 'react';
import { View, Image, Pressable, TextInput } from 'react-native';
import { T, Button, Card, Title, C } from '../components/Common';
import { PrivateImage } from '../components/PrivateImage';
import { RiskBadge } from '../components/RiskIndicator';
import { recordDate } from '../services/progress';
import { filterRecords, statusLabel } from '../services/records';

// Development cards and capture-date form, populated by the authenticated API.
export function RecordList({ records, onOpen, onAdd, busy, api, token, more, onMore }) {
  const [filter, setFilter] = useState('전체');
  const options = ['전체', '문진 진행 중', '분석 완료'];
  const list = filterRecords(records, filter);
  return <>
    <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
      {options.map(label => <Pressable key={label} accessibilityRole="button" accessibilityState={{ selected: filter === label }}
        onPress={() => setFilter(label)} style={{ paddingHorizontal: 13, paddingVertical: 8, backgroundColor: filter === label ? C.navy : 'white', borderRadius: 22 }}>
        <T style={{ fontSize: 12, color: filter === label ? 'white' : C.muted }}>{label} {filterRecords(records, label).length}건</T>
      </Pressable>)}
    </View>
    <T style={{ color: C.muted, fontSize: 11 }}>불러온 기록 기준이에요. 분석 완료는 상처의 회복 완료를 뜻하지 않아요.</T>
    <Button disabled={busy} onPress={onAdd}>새 상처 사진 분석</Button>
    {list.map(record => <Card key={record.id}>
      <Pressable accessibilityRole="button" accessibilityLabel={`${recordDate(record)} 기록 상세`} disabled={busy} onPress={() => onOpen(record.id)}
        style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View style={{ width: 48 }}><PrivateImage url={api.base + record.imagePath} token={token} height={48} /></View>
        <View style={{ flex: 1 }}><Title>{statusLabel(record)}</Title><T style={{ fontSize: 11, color: C.muted }}>{recordDate(record)}</T></View>
        <RiskBadge record={record} />
      </Pressable>
      <View style={{ flexDirection: 'row', paddingVertical: 10, borderTopWidth: 1, borderColor: C.line }}>
        {[[`${record.turn ?? 0}회`, '저장된 문진'], [statusLabel(record), '처리 상태']].map(([value, label]) =>
          <View key={label} style={{ flex: 1, alignItems: 'center', gap: 5 }}><T style={{ fontFamily: 'NotoSansKR_700Bold', fontSize: 13 }}>{value}</T><T style={{ color: C.muted, fontSize: 10 }}>{label}</T></View>)}
      </View>
      <Button secondary disabled={busy} onPress={() => onOpen(record.id)}>{record.status === 'COMPLETED' ? '결과·경과 기록 보기' : '문진 이어서 보기'}</Button>
    </Card>)}
    {!list.length && <Card><T>아직 해당 기록이 없어요.</T></Card>}
    {more && <Button secondary disabled={busy} onPress={onMore}>이전 기록 더 보기</Button>}
  </>;
}

export function NewWound({ photo, date, setDate, onChangePhoto, target, error, busy }) {
  return <>
    <View><T style={{ fontSize: 24, lineHeight: 34, fontFamily: 'NotoSansKR_700Bold' }}>{target ? '경과 기록 추가' : '신규 상처'}</T>
      <T style={{ color: C.muted, marginTop: 8 }}>{target ? '새로운 사진으로 같은 상처의 경과를 기록해요.' : '선택한 사진으로 새 상처 분석을 시작해요.\n기존 상처와 별도로 등록돼요.'}</T></View>
    <Card><Title>선택한 상처 사진</Title>
      {photo ? <Image accessibilityLabel="업로드할 상처 사진" source={{ uri: photo.uri }} style={{ width: '100%', height: 240, borderRadius: 12, backgroundColor: '#EDF0F5' }} resizeMode="contain" /> : <T>사진을 선택해주세요.</T>}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><T style={{ width: 70, flexShrink: 0 }}>촬영 날짜</T>
        <TextInput accessibilityLabel="사진 촬영일 (YYYY-MM-DD)" editable={!busy} value={date} onChangeText={setDate} placeholder="YYYY-MM-DD" maxLength={10}
          style={{ flex: 1, minWidth: 0, width: 0, textAlign: 'right', fontFamily: 'NotoSansKR_400Regular', color: C.navy, padding: 10, borderWidth: 1, borderColor: C.line, borderRadius: 8 }} /></View>
      <Pressable accessibilityRole="button" disabled={busy} onPress={onChangePhoto}><T style={{ color: C.navy, fontSize: 12 }}>사진 변경</T></Pressable>
    </Card>
    <Card style={{ backgroundColor: '#EAF0FA', borderWidth: 0 }}><T style={{ fontSize: 12, color: C.navy }}>ⓘ 사진과 촬영 날짜를 확인해주세요. 분석 결과는 의료진의 진단을 대신하지 않아요.</T></Card>
    {!!error && <T accessibilityRole="alert" style={{ color: '#C2414A' }}>{error}</T>}
  </>;
}

export function RecordDetail({ record, api, token, title, description, onZoom }) {
  if (!record) return <Card><T>기록을 찾을 수 없어요.</T></Card>;
  return <><View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}><Title>{title}</Title>{!record.result && <RiskBadge record={record} />}</View>
    <Card><Title>상처 사진</Title><PrivateImage url={api.base + record.imagePath} token={token} height={260} />
      <T>촬영 날짜 {recordDate(record)}</T><T style={{ color: C.muted }}>{description}</T>
      <Button secondary onPress={onZoom}>상처 사진 확대</Button>
    </Card>
  </>;
}
