import React, { useState } from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Card, Title, T, Button, C } from '../components/Common';
import { Field, PolicyLink } from '../components/ConsentControls';
import { ARTICLES, SOURCES, searchArticles } from '../data/encyclopedia';
export default function EncyclopediaScreen({ articleId, onOpen, onBack }) {
  const [query, setQuery] = useState(''), [category, setCategory] = useState('전체');
  const article = ARTICLES.find(a => a.id === articleId);
  if (article) return <>
    <Card style={s.intro}>
      <CategoryLabel>{article.category}</CategoryLabel>
      <T accessibilityRole="header" style={s.articleTitle}>{article.title}</T>
      <T style={s.lead}>{article.body}</T>
      {article.category === '피부 질환' && <T style={s.note}>이 항목은 정보 열람용이에요. 현재 사진 분석 모델의 진단 대상이 아니에요.</T>}
    </Card>
    {article.sections.map(section => <Card key={section.title} style={section.attention && s.attention}>
      <Title>{section.title}</Title>
      {section.bullets.map(text => <View key={text} style={s.bullet}>
        <T style={s.dot}>•</T><T style={s.bulletText}>{text}</T>
      </View>)}
    </Card>)}
    <Card>
      <Title>공식 자료에서 더 알아보기</Title>
      <T style={s.note}>자세한 설명과 그림은 출처 기관의 원문에서 확인할 수 있어요.</T>
      {article.sourceIds.map((id, index) => {
        const source = SOURCES[id];
        return <View key={id} style={[s.source, index > 0 && s.sourceDivider]}>
          <T style={s.sourceOrganization}>{source.organization}</T>
          <PolicyLink url={source.url}>{source.title} 원문 보기 ({source.language})</PolicyLink>
          {!!source.updatedAt && <T style={s.sourceDate}>원문 수정·검토일: {source.updatedAt}</T>}
          <T style={s.sourceDate}>앱에서 출처 확인: {source.checkedAt}</T>
        </View>;
      })}
    </Card>
    <T style={s.note}>공식 원문을 참고해 정리한 일반 정보예요. 개인별 진단·치료를 대신하지 않으며, 전문가 검수 전 연구·시연용이에요.</T>
    <Button secondary onPress={onBack}>백과 목록으로</Button>
  </>;
  const items = searchArticles(query, category);
  return <>
    <Card style={s.intro}>
      <T accessibilityRole="header" style={s.articleTitle}>피부·상처 백과</T>
      <T style={s.note}>질병관리청·NHS·미국 국립의학도서관의 자료를 참고해 증상과 관리 시 주의점을 정리했어요.</T>
      <Field label="백과 검색" placeholder="화상, 멍, 가려움…" value={query} onChangeText={setQuery} maxLength={80} />
      <View style={s.filters}>{['전체', '상처', '피부 질환'].map(value => <Pressable key={value}
        accessibilityRole="button" accessibilityState={{ selected: value === category }} onPress={() => setCategory(value)}
        style={({ pressed }) => [s.filter, value === category && s.filterSelected, { opacity: pressed ? 0.7 : 1 }]}>
        <T style={[s.filterText, value === category && s.filterTextSelected]}>{value}</T>
      </Pressable>)}</View>
    </Card>
    <T style={s.count}>{items.length}개 자료</T>
    {items.map(item => <Card key={item.id} style={s.listCard}>
      <CategoryLabel>{item.category}</CategoryLabel>
      <Title>{item.title}</Title><T>{item.summary}</T>
      <T style={s.sourceDate}>{SOURCES[item.sourceIds[0]].organization}</T>
      <Button secondary onPress={() => onOpen(item.id)}>{item.title} 자료 보기</Button>
    </Card>)}
    {!items.length && <Card><T>검색 결과가 없어요. 검색어를 줄이거나 필터를 바꿔 주세요.</T></Card>}
    <T style={s.note}>백과에 수록된 피부 질환이 사진 분석의 지원 유형을 뜻하지는 않아요.</T>
  </>;
}

function CategoryLabel({ children }) {
  return <View style={s.category}><T style={s.categoryText}>{children}</T></View>;
}
const s = StyleSheet.create({
  intro: { gap: 15, padding: 22 },
  articleTitle: { fontSize: 24, lineHeight: 34, fontFamily: 'NotoSansKR_700Bold' },
  lead: { fontSize: 15, lineHeight: 26 },
  note: { fontSize: 12, lineHeight: 21, color: C.muted },
  category: { alignSelf: 'flex-start', backgroundColor: '#EDF2FA', borderRadius: 6, paddingHorizontal: 9, paddingVertical: 3 },
  categoryText: { color: C.navy, fontSize: 11, lineHeight: 18, fontFamily: 'NotoSansKR_700Bold' },
  attention: { borderLeftWidth: 3, borderLeftColor: '#B88237' },
  bullet: { flexDirection: 'row', gap: 8 },
  dot: { color: C.muted, lineHeight: 25 },
  bulletText: { flex: 1, lineHeight: 25 },
  source: { gap: 2 },
  sourceDivider: { borderTopWidth: 1, borderTopColor: C.line, paddingTop: 14 },
  sourceOrganization: { fontSize: 13, fontFamily: 'NotoSansKR_700Bold' },
  sourceDate: { fontSize: 11, lineHeight: 19, color: C.muted },
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  filter: { minHeight: 44, borderRadius: 22, paddingHorizontal: 18, justifyContent: 'center', backgroundColor: '#F0F3F8' },
  filterSelected: { backgroundColor: C.navy },
  filterText: { fontSize: 13, color: C.navy },
  filterTextSelected: { color: '#FFF', fontFamily: 'NotoSansKR_700Bold' },
  count: { fontSize: 12, color: C.muted, marginLeft: 4 },
  listCard: { gap: 11 },
});
