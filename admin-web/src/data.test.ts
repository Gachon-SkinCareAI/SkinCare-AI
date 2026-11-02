import { describe, expect, it } from 'vitest'
import {
  createSeed,
  csv,
  filterDiagnoses,
  filterInquiries,
  parseStore,
  saveReply,
} from './data'

describe('와이어프레임 샘플 데이터', () => {
  it('진단과 문의가 존재하는 사용자와 분석 기록을 참조한다', () => {
    const store = createSeed()
    expect(store.diagnoses).toHaveLength(18)
    expect(new Set(store.diagnoses.map((d) => d.id)).size).toBe(18)
    for (const d of store.diagnoses)
      expect(store.users.some((u) => u.id === d.userId)).toBe(true)
    for (const q of store.inquiries) {
      expect(store.users.some((u) => u.id === q.userId)).toBe(true)
      if (q.diagnosisId)
        expect(store.diagnoses.some((d) => d.id === q.diagnosisId)).toBe(true)
    }
  })
  it('진단 검색은 대소문자와 공백을 무시하고 위험도와 함께 적용된다', () => {
    const store = createSeed()
    expect(
      filterDiagnoses(store.diagnoses, ' dx-20260929-0438 ', '응급').map(
        (d) => d.id,
      ),
    ).toEqual(['DX-20260929-0438'])
    expect(
      filterDiagnoses(store.diagnoses, 'dx-20260929-0438', '관찰'),
    ).toEqual([])
    expect(
      filterDiagnoses(store.diagnoses, '화상', '전체').every((d) =>
        d.condition.includes('화상'),
      ),
    ).toBe(true)
  })
  it('문의 유형과 답변 상태를 개별적으로 필터링한다', () => {
    const { inquiries } = createSeed()
    expect(filterInquiries(inquiries, '답변 완료').map((q) => q.id)).toEqual([
      '002',
    ])
    expect(filterInquiries(inquiries, '답변 대기')).toHaveLength(3)
    expect(
      filterInquiries(inquiries, 'AI 분석 결과 문의', '다리').map((q) => q.id),
    ).toEqual(['001'])
    expect(filterInquiries(inquiries, '기타')).toHaveLength(1)
  })
})
describe('문의 답변 저장', () => {
  it('원본을 변경하지 않고 답변을 저장하며 대기 필터에서 제외한다', () => {
    const store = createSeed()
    const updated = saveReply(store, '001', '  답변 샘플입니다.  ')
    expect(store.inquiries[0].reply).toBe('')
    expect(updated.inquiries[0].reply).toBe('답변 샘플입니다.')
    expect(
      filterInquiries(updated.inquiries, '답변 대기').some(
        (q) => q.id === '001',
      ),
    ).toBe(false)
    expect(filterInquiries(updated.inquiries, '답변 완료')).toHaveLength(2)
  })
  it('빈 답변, 초과 길이와 잘못된 문의 번호를 거부한다', () => {
    const store = createSeed()
    expect(() => saveReply(store, '001', '   ')).toThrow('답변을 입력')
    expect(() => saveReply(store, '001', '가'.repeat(2001))).toThrow('2,000자')
    expect(() => saveReply(store, 'missing', '답변')).toThrow(
      '찾을 수 없습니다',
    )
  })
})
describe('브라우저 저장 복원', () => {
  it('답변과 계정 상태 변경을 유지한다', () => {
    const store = saveReply(createSeed(), '003', '기록을 확인할 수 있습니다.')
    store.users[0].active = false
    store.diagnoses[0].status = '진행중'
    expect(parseStore(JSON.stringify(store))).toEqual(store)
  })
  it.each([
    null,
    '{broken',
    '{}',
    '{"version":1}',
    '{"version":2,"users":[null],"diagnoses":[],"inquiries":[],"facilities":[]}',
  ])('손상 또는 이전 형식 %s을 안전하게 처리한다', (raw) => {
    expect(parseStore(raw)).toEqual(createSeed())
  })
  it('존재하지 않는 관계와 신뢰도 범위 오류를 거부한다', () => {
    const store = createSeed()
    store.diagnoses[0].confidence = 101
    expect(parseStore(JSON.stringify(store))).toEqual(createSeed())
    store.diagnoses[0].confidence = 91
    store.inquiries[0].diagnosisId = 'missing'
    expect(parseStore(JSON.stringify(store))).toEqual(createSeed())
  })
})
describe('CSV 다운로드 데이터', () => {
  it('한글 BOM, 따옴표와 줄바꿈을 보존하고 수식 주입을 방지한다', () => {
    expect(
      csv([
        ['이름', '메모'],
        ['김,서연', 'a"b\nc'],
        ['=1+2', '@SUM(1)'],
      ]),
    ).toBe('\uFEFF"이름","메모"\r\n"김,서연","a""b\nc"\r\n"\'=1+2","\'@SUM(1)"')
  })
})
