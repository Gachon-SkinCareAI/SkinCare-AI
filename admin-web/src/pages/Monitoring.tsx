import { Badge, Metric, Progress } from "../components";
import type { AiMetric, Store } from "../data";

const COLORS = ["navy", "red", "blue", "green", "purple", "orange"];

// AI 서버 상태 코드를 화면 문구와 배지 색으로 변환
const HEALTH: Record<string, [string, string]> = {
  ok: ["정상", "green"],
  degraded: ["일부 저하", "orange"],
  starting: ["시작 중", "orange"],
  unreachable: ["연결 불가", "red"],
};

// 지표 값과 단위를 하나의 문자열로 표시 (값이 없으면 대시)
function metricValue(metric: AiMetric) {
  return metric.value === null || metric.value === undefined
    ? "—"
    : `${metric.value}${metric.unit ?? ""}`;
}

export default function Monitoring({ store }: { store: Store }) {
  const { metrics, pipelines, health } = store.monitoring;
  const metricEntries = Object.entries(metrics);
  const [healthLabel, healthTone] = HEALTH[health.status ?? ""] ?? [
    "확인 불가",
    "gray",
  ];
  const models = Object.entries(health.models ?? {});
  return (
    <>
      <section className="panel pipeline-panel">
        <div className="panel-heading">
          <h2>AI 서버 상태</h2>
          <Badge tone={healthTone}>{healthLabel}</Badge>
        </div>
        {health.status === "unreachable" || !health.status ? (
          <p className="muted detail-empty">
            AI 서버에 연결할 수 없어 모델 정보를 표시할 수 없습니다. AI 서버
            실행 상태를 확인해 주세요.
          </p>
        ) : (
          <>
            <p className="muted">
              모델{" "}
              {models.length
                ? models
                    .map(([name, version]) => `${name} ${version}`)
                    .join(" · ")
                : "정보 없음"}
            </p>
            <p className="muted">
              문항 뱅크 {health.bankVersion ?? "—"} · 케어 카드{" "}
              {health.cardVersion ?? "—"} · 진행 중 추론{" "}
              {health.inflight ?? "—"} / {health.maxInflight ?? "—"}
            </p>
          </>
        )}
      </section>
      <section className="metrics-grid three" aria-label="AI 성능 지표">
        {metricEntries.map(([key, metric], i) => (
          <Metric
            key={key}
            icon="📊"
            value={metricValue(metric)}
            label={key.replaceAll("_", " ")}
            hint={metric.description ?? ""}
            change={metric.change || undefined}
            color={COLORS[i % COLORS.length]}
          />
        ))}
      </section>
      {!metricEntries.length && (
        <section className="panel">
          <p className="muted detail-empty">
            표시할 AI 성능 지표가 없습니다. 모델 평가 결과가 등록되면 여기에
            나타납니다.
          </p>
        </section>
      )}
      <section className="panel pipeline-panel">
        <div className="panel-heading">
          <h2>AI 파이프라인 상태</h2>
          <span className="sample-chip">API 상태</span>
        </div>
        <div className="pipeline-list">
          {pipelines.map((item) => (
            <div className="pipeline-row" key={item.id ?? item.engineName}>
              <span
                className={item.status === "HEALTHY" ? "green-dot" : "red-dot"}
              />
              <strong>{item.engineName}</strong>
              <span className="pipeline-latency">{item.avgLatency ?? "—"}</span>
              <Progress value={item.resourceUsageRate ?? 0} />
              <span>{item.resourceUsageRate ?? 0}%</span>
            </div>
          ))}
          {!pipelines.length && (
            <p className="muted detail-empty">
              등록된 파이프라인 상태가 없습니다.
            </p>
          )}
        </div>
      </section>
      <p className="aggregate-note">
        백엔드 AI 모니터링 API에서 조회한 운영 데이터입니다
      </p>
    </>
  );
}
