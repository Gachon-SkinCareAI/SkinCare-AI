export const DEMO_DATE = '2026-09-29'
export const STORAGE_KEY = 'gachon-skincare-admin-wireframe-v2'
export const risks = ['응급', '경고', '관찰'] as const
export type Risk = (typeof risks)[number]
export type Diagnosis = {
  id: string
  userId: string
  time: string
  condition: string
  area: string
  risk: Risk
  confidence: number
  status: '처리완료' | '진행중' | '완료' | '알림발송'
  connection: string
}
export type User = {
  id: string
  name: string
  joinedAt: string
  diagnoses: number
  risk: Risk
  plan: 'Premium' | 'Free'
  active: boolean
}
export type Facility = {
  id: string
  name: string
  type: '피부과' | '종합병원' | '약국'
  region: string
  night: boolean
  emergency: boolean
  active: boolean
  address: string
  phone: string
}
export const inquiryCategories = [
  'AI 분석 결과 문의',
  'AI 분석 정확도 문의',
  '기타',
] as const
export type InquiryCategory = (typeof inquiryCategories)[number]
export type Inquiry = {
  id: string
  category: InquiryCategory
  userId: string
  author: string
  subject: string
  content: string
  diagnosisId: string | null
  date: string
  reply: string
}
export type Store = {
  version: 2
  users: User[]
  diagnoses: Diagnosis[]
  facilities: Facility[]
  inquiries: Inquiry[]
}
export type Detail =
  | { type: 'diagnosis' | 'user' | 'facility' | 'inquiry'; id: string }
  | { type: 'sync' }
export type Page =
  | 'dashboard'
  | 'diagnoses'
  | 'monitoring'
  | 'facilities'
  | 'users'
  | 'inquiries'
export type Navigate = (page: Page, risk?: Risk) => void
export type OpenDetail = (detail: Detail) => void

// Aggregate figures reproduce the supplied wireframe. The tables contain representative fixtures,
// not all 12,847 accounts or 1,284 institutions represented by these illustrative totals.
export const demoMetrics = {
  totalUsers: 12847,
  todayDiagnoses: 438,
  emergencies: 23,
  premium: 2341,
  dau: 4218,
  retention: 64.3,
  facilities: 1284,
  night: 342,
  pharmacies: 89,
}
export const dailyDiagnoses = [280, 310, 295, 420, 380, 405, 438]
export const recalls = [94.8, 95.3, 95.1, 95.8, 96.4, 96.1, 96.2]

export function createSeed(): Store {
  const users: User[] = [
    {
      id: 'USR-2847',
      name: '김민서',
      joinedAt: '2025-11-03',
      diagnoses: 24,
      risk: '응급',
      plan: 'Premium',
      active: true,
    },
    {
      id: 'USR-1923',
      name: '이준혁',
      joinedAt: '2026-01-15',
      diagnoses: 8,
      risk: '경고',
      plan: 'Free',
      active: true,
    },
    {
      id: 'USR-3102',
      name: '박소연',
      joinedAt: '2026-03-22',
      diagnoses: 15,
      risk: '관찰',
      plan: 'Premium',
      active: true,
    },
    {
      id: 'USR-0881',
      name: '최동훈',
      joinedAt: '2025-09-10',
      diagnoses: 31,
      risk: '관찰',
      plan: 'Premium',
      active: false,
    },
    {
      id: 'USR-2214',
      name: '정유진',
      joinedAt: '2026-06-08',
      diagnoses: 5,
      risk: '관찰',
      plan: 'Free',
      active: true,
    },
    {
      id: 'USR-3341',
      name: '강지우',
      joinedAt: '2026-07-12',
      diagnoses: 12,
      risk: '응급',
      plan: 'Free',
      active: true,
    },
    ...[
      '윤서준',
      '장다은',
      '임지호',
      '한소윤',
      '오현우',
      '서유진',
      '신도현',
      '권채원',
      '황시우',
    ].map(
      (name, i): User => ({
        id: `USR-${4000 + i}`,
        name,
        joinedAt: `2026-08-${String(i + 10).padStart(2, '0')}`,
        diagnoses: 3 + i * 2,
        risk: risks[i % 3],
        plan: i % 2 ? 'Premium' : 'Free',
        active: i !== 7,
      }),
    ),
  ]
  const conditions = [
    '3도 화상',
    '2도 화상',
    '접촉성 피부염',
    '찰과상',
    '여드름성 피부',
    '감염성 상처',
  ]
  const areas = ['팔 (전완부)', '손등', '얼굴', '무릎', '얼굴', '다리']
  const times = ['14:32', '14:28', '14:15', '13:58', '13:42', '13:30']
  const diagnoses: Diagnosis[] = Array.from({ length: 18 }, (_, i) => ({
    id: `DX-20260929-${String(438 - i).padStart(4, '0')}`,
    userId: users[i % users.length].id,
    time:
      i < 6
        ? times[i]
        : `${String(13 - Math.floor((i - 6) / 4)).padStart(2, '0')}:${String(25 - (i % 4) * 5).padStart(2, '0')}`,
    condition: conditions[i % 6],
    area: areas[i % 6],
    risk: (['응급', '응급', '경고', '관찰', '관찰', '응급'] as const)[i % 6],
    confidence: [91, 94, 87, 96, 89, 92][i % 6],
    status: (
      ['처리완료', '진행중', '완료', '완료', '완료', '알림발송'] as const
    )[i % 6],
    connection: [
      '응급실 연결 완료',
      '병원 연결 중',
      '검토 완료',
      '분석 완료',
      '분석 완료',
      '경고 알림 발송',
    ][i % 6],
  }))
  const facilityNames = [
    '서울사랑피부과',
    '행복한 피부과',
    '우리동 종합병원',
    '강남피부과클리닉',
    '새벽공공약국',
    '가천의료센터',
    '연세맑은피부과',
    '분당중앙병원',
    '온누리약국',
    '송파봄피부과',
    '서울열린병원',
    '달빛공공약국',
  ]
  const facilities: Facility[] = facilityNames.map((name, i) => ({
    id: `MED-${String(i + 1).padStart(4, '0')}`,
    name,
    type: name.includes('약국')
      ? '약국'
      : name.includes('병원') || name.includes('센터')
        ? '종합병원'
        : '피부과',
    region: ['강남구', '서초구', '송파구', '강남구', '마포구', '성남시'][i % 6],
    night: i !== 3 && i !== 6,
    emergency: [2, 5, 7, 10].includes(i),
    active: i !== 10,
    address: `서울·경기 ${['강남구', '서초구', '송파구', '강남구', '마포구', '성남시'][i % 6]} 샘플로 ${10 + i}`,
    phone: '전화번호 미연결 (샘플)',
  }))
  const inquiries: Inquiry[] = [
    {
      id: '001',
      category: 'AI 분석 결과 문의',
      userId: users[5].id,
      author: '사용자 A',
      subject: '왜 다리 상처가 경고 단계로 나왔나요?',
      content:
        '다리 상처를 촬영했는데 경고 단계로 표시됐어요. 분석 결과에서 위험도가 어떻게 정해지는지 궁금합니다.',
      diagnosisId: diagnoses[5].id,
      date: '2026-09-29',
      reply: '',
    },
    {
      id: '002',
      category: 'AI 분석 정확도 문의',
      userId: users[0].id,
      author: '김스킨',
      subject: '촬영 각도에 따라 결과가 달라질 수 있나요?',
      content:
        '같은 팔 부위를 다른 각도로 촬영했더니 분석 결과가 달라졌습니다. 사진을 어떻게 찍어야 하나요?',
      diagnosisId: diagnoses[0].id,
      date: '2026-09-28',
      reply:
        '문의 감사합니다. 촬영 각도와 조명, 초점에 따라 이미지 분석 결과가 달라질 수 있습니다. 이 화면은 기능 확인을 위한 데모이며 실제 진단 결과는 제공하지 않습니다.',
    },
    {
      id: '003',
      category: '기타',
      userId: users[2].id,
      author: '박소연',
      subject: '이전 분석 기록은 어디에서 확인하나요?',
      content:
        '이전에 진행했던 분석 기록을 다시 확인하고 싶습니다. 확인 방법을 안내해 주세요.',
      diagnosisId: null,
      date: '2026-09-28',
      reply: '',
    },
    {
      id: '004',
      category: 'AI 분석 결과 문의',
      userId: users[1].id,
      author: '이준혁',
      subject: '분석 결과의 신뢰도는 무엇을 뜻하나요?',
      content: '결과 옆의 신뢰도 숫자가 어떤 의미인지 궁금합니다.',
      diagnosisId: diagnoses[1].id,
      date: '2026-09-27',
      reply: '',
    },
  ]
  return { version: 2, users, diagnoses, facilities, inquiries }
}

const isText = (v: unknown): v is string => typeof v === 'string'
const isRisk = (v: unknown): v is Risk => risks.includes(v as Risk)
const isDay = (v: unknown) =>
  isText(v) && /^\d{4}-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(v))
export function parseStore(raw: string | null): Store {
  if (!raw) return createSeed()
  try {
    const s = JSON.parse(raw) as Store
    if (
      s?.version !== 2 ||
      !Array.isArray(s.users) ||
      !Array.isArray(s.diagnoses) ||
      !Array.isArray(s.facilities) ||
      !Array.isArray(s.inquiries)
    )
      return createSeed()
    if (
      !s.users.every(
        (u) =>
          u &&
          isText(u.id) &&
          isText(u.name) &&
          isDay(u.joinedAt) &&
          isRisk(u.risk) &&
          Number.isInteger(u.diagnoses) &&
          u.diagnoses >= 0 &&
          ['Premium', 'Free'].includes(u.plan) &&
          typeof u.active === 'boolean',
      )
    )
      return createSeed()
    const ids = new Set(s.users.map((u) => u.id))
    if (
      ids.size !== s.users.length ||
      !s.diagnoses.every(
        (d) =>
          d &&
          isText(d.id) &&
          ids.has(d.userId) &&
          /^\d{2}:\d{2}$/.test(d.time) &&
          isText(d.condition) &&
          isText(d.area) &&
          isRisk(d.risk) &&
          Number.isFinite(d.confidence) &&
          d.confidence >= 0 &&
          d.confidence <= 100 &&
          ['처리완료', '진행중', '완료', '알림발송'].includes(d.status) &&
          isText(d.connection),
      )
    )
      return createSeed()
    const diagnosisIds = new Set(s.diagnoses.map((d) => d.id))
    if (
      diagnosisIds.size !== s.diagnoses.length ||
      !s.facilities.every(
        (f) =>
          f &&
          isText(f.id) &&
          isText(f.name) &&
          ['피부과', '종합병원', '약국'].includes(f.type) &&
          isText(f.region) &&
          [f.night, f.emergency, f.active].every(
            (v) => typeof v === 'boolean',
          ) &&
          isText(f.address) &&
          isText(f.phone),
      )
    )
      return createSeed()
    if (
      new Set(s.facilities.map((f) => f.id)).size !== s.facilities.length ||
      !s.inquiries.every(
        (q) =>
          q &&
          isText(q.id) &&
          inquiryCategories.includes(q.category) &&
          ids.has(q.userId) &&
          isText(q.author) &&
          isText(q.subject) &&
          isText(q.content) &&
          (q.diagnosisId === null || diagnosisIds.has(q.diagnosisId)) &&
          isDay(q.date) &&
          isText(q.reply),
      )
    )
      return createSeed()
    if (new Set(s.inquiries.map((q) => q.id)).size !== s.inquiries.length)
      return createSeed()
    return s
  } catch {
    return createSeed()
  }
}

export function filterDiagnoses(
  rows: Diagnosis[],
  query: string,
  risk: string,
) {
  const q = query.trim().toLocaleLowerCase()
  return rows.filter(
    (d) =>
      `${d.id} ${d.userId} ${d.condition} ${d.area}`
        .toLocaleLowerCase()
        .includes(q) &&
      (risk === '전체' || d.risk === risk),
  )
}
export function filterInquiries(rows: Inquiry[], filter: string, query = '') {
  const q = query.trim().toLocaleLowerCase()
  return rows.filter(
    (row) =>
      (filter === '전체' ||
        (filter === '답변 대기' && !row.reply.trim()) ||
        (filter === '답변 완료' && !!row.reply.trim()) ||
        row.category === filter) &&
      `${row.id} ${row.author} ${row.subject} ${row.content}`
        .toLocaleLowerCase()
        .includes(q),
  )
}
export function saveReply(store: Store, id: string, reply: string): Store {
  const text = reply.trim()
  if (!text) throw new Error('답변을 입력해 주세요.')
  if (text.length > 2000)
    throw new Error('답변은 2,000자 이내로 입력해 주세요.')
  if (!store.inquiries.some((q) => q.id === id))
    throw new Error('문의를 찾을 수 없습니다.')
  return {
    ...store,
    inquiries: store.inquiries.map((q) =>
      q.id === id ? { ...q, reply: text } : q,
    ),
  }
}
export function csv(rows: (string | number)[][]): string {
  return (
    '\uFEFF' +
    rows
      .map((row) =>
        row
          .map((value) => {
            const text = String(value)
            const safe = /^[\s]*[=+\-@\t\r]/.test(text) ? `'${text}` : text
            return `"${safe.replaceAll('"', '""')}"`
          })
          .join(','),
      )
      .join('\r\n')
  )
}
export function downloadCsv(filename: string, rows: (string | number)[][]) {
  const url = URL.createObjectURL(
    new Blob([csv(rows)], { type: 'text/csv;charset=utf-8;' }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
