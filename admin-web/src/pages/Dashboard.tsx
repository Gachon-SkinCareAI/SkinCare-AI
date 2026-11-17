import { ArrowRight, ClipboardList, Mail, PlugZap, Users } from "lucide-react";
import { Badge, Metric, Progress } from "../components";
import {
  analysisStatusLabel,
  analysisStatuses,
  dayKey,
  formatTime,
  type Navigate,
  type OpenDetail,
  type Store,
} from "../data";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const STATUS_TONE: Record<string, string> = {
  COMPLETED: "green",
  QUESTIONNAIRE: "blue",
  RETRY_REQUIRED: "orange",
  CREATED: "gray",
};

// 분석 관련 지표는 앱의 실제 분석 기록을 운영 정보로만 집계한다
// (건수·처리 상태·시각. 위험도·의심 유형 같은 분석 결과는 개인정보 처리방침에 따라 집계하지 않는다)
export default function Dashboard({
  store,
  navigate,
}: {
  store: Store;
  navigate: Navigate;
  open: OpenDetail;
}) {
  const m = store.dashboard;
  const summary = store.analysisSummary;

  // 서버가 집계한 최근 7일(오늘 포함) 날짜별 분석 건수
  const today = new Date();
  const days = Array.from({ length: 7 }, (_, i) => {
    const date = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate() - (6 - i),
    );
    const key = dayKey(date);
    return {
      key,
      label: WEEKDAYS[date.getDay()],
      count: Number(summary.daily.find((d) => d.date === key)?.count || 0),
    };
  });
  const maxCount = Math.max(1, ...days.map((d) => d.count));

  // 전체 분석 기록의 처리 상태 분포
  const total = analysisStatuses.reduce(
    (sum, s) => sum + Number(summary.counts[s] || 0),
    0,
  );
  const distribution = analysisStatuses.map((status) => {
    const count = Number(summary.counts[status] || 0);
    return {
      status,
      name: analysisStatusLabel[status],
      tone: STATUS_TONE[status],
      count,
      percent: total ? Math.round((count / total) * 100) : 0,
    };
  });

  const recent = store.analyses.slice(0, 5);

  return (
    <>
      <section className="metrics-grid four" aria-label="대시보드 주요 지표">
        <Metric
          icon={<Users size={20} />}
          value={String(m.totalUsers ?? 0)}
          label="총 사용자"
          hint="누적 가입자"
        />
        <Metric
          icon={<ClipboardList size={20} />}
          value={String(m.todayDiagnoses ?? 0)}
          label="오늘 분석 수"
          hint="오늘 시작된 분석"
          color="blue"
        />
        <Metric
          icon={<PlugZap size={20} />}
          value={String(m.retryRequired ?? 0)}
          label="재연결 필요"
          hint="AI 연결 확인이 필요한 분석"
          color="orange"
        />
        <Metric
          icon={<Mail size={20} />}
          value={String(m.waitingInquiries ?? 0)}
          label="답변 대기 문의"
          hint="답변이 필요한 문의"
          color="orange"
        />
      </section>
      <div className="dashboard-charts">
        <section className="panel daily-panel">
          <div className="panel-heading">
            <h2>일별 분석 건수 (최근 7일)</h2>
            <span className="muted">건</span>
          </div>
          <div
            className="daily-chart"
            role="img"
            aria-label={`최근 7일 분석 건수: ${days.map((d) => `${d.label} ${d.count}`).join(", ")}건`}
          >
            {days.map((day, i) => (
              <div className="daily-column" key={day.key}>
                <div className="bar-zone">
                  <div
                    className={`daily-bar ${i === 6 ? "last" : ""}`}
                    style={{ height: `${(day.count / maxCount) * 100}%` }}
                  >
                    <span>{day.count}</span>
                  </div>
                </div>
                <small>{day.label}</small>
              </div>
            ))}
          </div>
        </section>
        <section className="panel risk-panel">
          <div className="panel-heading">
            <h2>처리 상태 분포 (전체 {total}건)</h2>
          </div>
          <div className="risk-distribution">
            {distribution.map((item) => (
              <div key={item.status}>
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
          <h2>최근 분석 기록</h2>
          <button className="text-button" onClick={() => navigate("diagnoses")}>
            전체보기 <ArrowRight size={13} />
          </button>
        </div>
        <div className="emergency-list">
          {recent.map((a) => (
            <div key={a.id} className="emergency-row">
              <span className="red-dot" aria-hidden="true" />
              <div>
                <strong className="mono">{a.id}</strong>
                <time>{formatTime(a.createdAt)}</time>
                <p>문진 {a.turn}회</p>
              </div>
              <Badge tone={STATUS_TONE[a.status] || "gray"}>
                {analysisStatusLabel[a.status] || a.status}
              </Badge>
            </div>
          ))}
          {!recent.length && (
            <p className="panel-note solo">아직 분석 기록이 없습니다.</p>
          )}
        </div>
      </section>
      <p className="aggregate-note">
        분석 지표는 건수와 처리 상태만 집계합니다. 분석 결과·사진·문진과 사용자 정보는 표시하지
        않습니다.
      </p>
    </>
  );
}
