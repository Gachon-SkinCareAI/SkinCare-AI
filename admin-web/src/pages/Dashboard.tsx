import { ArrowRight } from 'lucide-react'
import { Badge, Metric, Progress } from '../components'
import {
  dailyDiagnoses,
  demoMetrics as m,
  type Navigate,
  type OpenDetail,
  type Store,
} from '../data'

export default function Dashboard({
  store,
  navigate,
  open,
}: {
  store: Store
  navigate: Navigate
  open: OpenDetail
}) {
  const emergencies = store.diagnoses
    .filter((d) => d.risk === '응급')
    .slice(0, 3)
  return (
    <>
      <section className="metrics-grid four" aria-label="대시보드 주요 지표">
        <Metric
          icon="👤"
          value={m.totalUsers.toLocaleString()}
          label="총 사용자"
          hint="이번 달 +1,234명"
          change="+10.6%"
        />
        <Metric
          icon="📋"
          value={String(m.todayDiagnoses)}
          label="오늘 진단 수"
          hint="어제 대비 +52건"
          change="+13.5%"
          color="blue"
        />
        <Metric
          icon="🚨"
          value={String(m.emergencies)}
          label="응급 케이스"
          hint="즉시 처리 필요"
          change="+3"
          color="red"
        />
        <Metric
          icon="🤖"
          value="91.4%"
          label="AI 정확도"
          hint="위험군 Recall 96.2%"
          change="+0.8%"
          color="green"
        />
      </section>
      <div className="dashboard-charts">
        <section className="panel daily-panel">
          <div className="panel-heading">
            <h2>일별 진단 건수 (최근 7일)</h2>
            <span className="muted">건</span>
          </div>
          <div
            className="daily-chart"
            role="img"
            aria-label="최근 7일 진단 건수: 월 280, 화 310, 수 295, 목 420, 금 380, 토 405, 일 438건"
          >
            {dailyDiagnoses.map((count, i) => (
              <div className="daily-column" key={i}>
                <div className="bar-zone">
                  <div
                    className={`daily-bar ${i === 6 ? 'last' : ''}`}
                    style={{ height: `${(count / 438) * 100}%` }}
                  >
                    <span>{count}</span>
                  </div>
                </div>
                <small>{['월', '화', '수', '목', '금', '토', '일'][i]}</small>
              </div>
            ))}
          </div>
        </section>
        <section className="panel risk-panel">
          <div className="panel-heading">
            <h2>위험도 분포 (오늘)</h2>
          </div>
          <div className="risk-distribution">
            {[
              {
                name: '관찰 (Observation)',
                count: 289,
                percent: 66,
                tone: 'green',
              },
              {
                name: '경고 (Warning)',
                count: 126,
                percent: 29,
                tone: 'orange',
              },
              { name: '응급 (Emergency)', count: 23, percent: 5, tone: 'red' },
            ].map((item) => (
              <div key={item.name}>
                <div>
                  <span>{item.name}</span>
                  <strong>
                    {item.count}건 ({item.percent}%)
                  </strong>
                </div>
                <Progress value={item.percent} tone={item.tone} />
              </div>
            ))}
          </div>
        </section>
      </div>
      <section className="panel emergency-panel">
        <div className="panel-heading">
          <h2>🚨 최근 응급 케이스</h2>
          <button
            className="text-button"
            onClick={() => navigate('diagnoses', '응급')}
          >
            전체보기 <ArrowRight size={13} />
          </button>
        </div>
        <div className="emergency-list">
          {emergencies.map((d) => (
            <button
              key={d.id}
              className="emergency-row"
              onClick={() => open({ type: 'diagnosis', id: d.id })}
            >
              <span className="red-dot" />
              <div>
                <strong>{d.userId}</strong>
                <time>{d.time}</time>
                <p>
                  {d.condition} · {d.area}
                </p>
              </div>
              <Badge tone="gray">{d.connection}</Badge>
              <span className="sr-only">진단 상세 보기</span>
            </button>
          ))}
        </div>
      </section>
      <p className="aggregate-note">
        지표는 와이어프레임의 예시 수치이며, 목록에는 대표 샘플 데이터가
        표시됩니다.
      </p>
    </>
  )
}
