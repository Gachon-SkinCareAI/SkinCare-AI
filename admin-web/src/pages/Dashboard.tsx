import { ArrowRight } from "lucide-react";
import { Badge, Metric, Progress } from "../components";
import { dayKey, type Navigate, type OpenDetail, type Store } from "../data";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

export default function Dashboard({
  store,
  navigate,
  open,
}: {
  store: Store;
  navigate: Navigate;
  open: OpenDetail;
}) {
  const m = store.dashboard;
  const emergencies = store.diagnoses
    .filter((d) => d.risk === "응급")
    .slice(0, 3);

  // 불러온 최근 진단 목록을 날짜별로 집계해 최근 7일(오늘 포함) 막대를 만든다
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
      count: store.diagnoses.filter((d) => d.time.slice(0, 10) === key).length,
    };
  });
  const maxCount = Math.max(1, ...days.map((d) => d.count));

  // 위험도 분포는 제목("오늘")과 같이 오늘 진단만 집계한다
  const todays = store.diagnoses.filter(
    (d) => d.time.slice(0, 10) === dayKey(today),
  );
  const distribution = [
    { name: "관찰 (Observation)", risk: "관찰", tone: "green" },
    { name: "경고 (Warning)", risk: "경고", tone: "orange" },
    { name: "응급 (Emergency)", risk: "응급", tone: "red" },
  ].map((item) => {
    const count = todays.filter((d) => d.risk === item.risk).length;
    return {
      ...item,
      count,
      percent: todays.length ? Math.round((count / todays.length) * 100) : 0,
    };
  });

  return (
    <>
      <section className="metrics-grid four" aria-label="대시보드 주요 지표">
        <Metric
          icon="👤"
          value={String(m.totalUsers ?? 0)}
          label="총 사용자"
          hint="누적 가입자"
        />
        <Metric
          icon="📋"
          value={String(m.todayDiagnoses ?? 0)}
          label="오늘 진단 수"
          hint="오늘 접수된 분석"
          color="blue"
        />
        <Metric
          icon="🚨"
          value={String(m.emergencies ?? 0)}
          label="응급 케이스"
          hint="즉시 처리 필요"
          color="red"
        />
        <Metric
          icon="✉"
          value={String(m.waitingInquiries ?? 0)}
          label="답변 대기 문의"
          hint="답변이 필요한 문의"
          color="orange"
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
            aria-label={`최근 7일 진단 건수: ${days.map((d) => `${d.label} ${d.count}`).join(", ")}건`}
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
            <h2>위험도 분포 (오늘)</h2>
          </div>
          <div className="risk-distribution">
            {distribution.map((item) => (
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
            onClick={() => navigate("diagnoses", "응급")}
          >
            전체보기 <ArrowRight size={13} />
          </button>
        </div>
        <div className="emergency-list">
          {emergencies.map((d) => (
            <button
              key={d.id}
              className="emergency-row"
              onClick={() => open({ type: "diagnosis", id: d.id })}
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
        백엔드에서 조회한 관리자 운영 데이터입니다
      </p>
    </>
  );
}
