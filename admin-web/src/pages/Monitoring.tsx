import { BarChart3 } from "lucide-react";
import { Badge, Metric, Progress } from "../components";
import type { AiHealth, AiMetric, AiPipeline, Store } from "../data";

const COLORS = ["navy", "red", "blue", "green", "purple", "orange"];

// AI 서버 상태 코드를 화면 문구와 배지 색으로 변환
const HEALTH: Record<string, [string, string]> = {
  ok: ["정상", "green"],
  degraded: ["일부 저하", "orange"],
  starting: ["시작 중", "orange"],
  unreachable: ["연결 불가", "red"],
};

// 지표 키를 화면 문구로 변환 (모르는 키는 밑줄만 공백으로 바꾼다)
const METRIC_LABELS: Record<string, string> = {
  macro_f1: "분류 Macro F1",
  accuracy: "정확도",
  precision: "정밀도",
  recall: "재현율",
  iou: "IoU",
  map50: "mAP@0.5",
  unknown_rate: "UNKNOWN 비율",
  burn_recall: "화상 재현율",
  open_wound_recall: "벌어진 상처 재현율",
};

// 지표 값과 단위를 하나의 문자열로 표시 (값이 없으면 대시)
function metricValue(metric: AiMetric) {
  return metric.value === null || metric.value === undefined
    ? "—"
    : `${metric.value}${metric.unit ?? ""}`;
}

// 파이프라인 한 줄에 표시할 값
type PipelineRow = {
  key: string;
  name: string;
  detail: string;
  healthy: boolean;
  statusText: string;
  latency?: string | null;
  usage?: number | null;
};

// 파이프라인 상태 코드를 화면 문구로 변환
function rowFromPipeline(item: AiPipeline): PipelineRow {
  const healthy = item.status === "HEALTHY";
  return {
    key: String(item.id ?? item.engineName),
    name: item.engineName,
    detail: "",
    healthy,
    statusText: healthy ? "가동 중" : "점검 필요",
    latency: item.avgLatency,
    usage: item.resourceUsageRate ?? 0,
  };
}

// 등록된 파이프라인 값이 없으면 AI 서버 상태에서 같은 정보를 만든다
// (모델 적재 상태와 진행 중 추론 수는 AI 서버가 그대로 알려 준다)
function rowsFromHealth(health: AiHealth): PipelineRow[] {
  const up = health.status === "ok";
  const text = up ? "가동 중" : "점검 필요";
  const models = health.models ?? {};
  const rows: PipelineRow[] = [
    ["detect", "환부 탐지 모델"],
    ["classify", "유형 분류 모델"],
  ]
    .filter(([key]) => models[key])
    .map(([key, name]) => ({
      key,
      name,
      detail: models[key],
      healthy: up,
      statusText: text,
    }));
  if (health.maxInflight) {
    const used = health.inflight ?? 0;
    rows.push({
      key: "inflight",
      name: "동시 추론",
      detail: `${used} / ${health.maxInflight} 사용 중`,
      healthy: up,
      statusText: text,
      usage: Math.round((used / health.maxInflight) * 100),
    });
  }
  return rows;
}

// 사용률에 맞는 막대 색
function usageTone(usage: number) {
  return usage >= 85 ? "red" : usage >= 60 ? "orange" : "green";
}

export default function Monitoring({ store }: { store: Store }) {
  const { metrics, pipelines, health } = store.monitoring;
  const metricEntries = Object.entries(metrics);
  const [healthLabel, healthTone] = HEALTH[health.status ?? ""] ?? [
    "확인 불가",
    "gray",
  ];
  const reachable = Boolean(health.status) && health.status !== "unreachable";
  const rows: PipelineRow[] = pipelines.length
    ? pipelines.map(rowFromPipeline)
    : reachable
      ? rowsFromHealth(health)
      : [];
  const live = !pipelines.length && rows.length > 0;
  const inflight = health.inflight ?? 0;
  const maxInflight = health.maxInflight ?? 0;
  const items: [string, string][] = [
    ["탐지 모델", health.models?.detect ?? "정보 없음"],
    ["분류 모델", health.models?.classify ?? "정보 없음"],
    ["문항 뱅크", health.bankVersion ?? "—"],
    ["케어 카드", health.cardVersion ?? "—"],
  ];
  return (
    <>
      <section className="panel pipeline-panel">
        <div className="panel-heading">
          <h2>AI 서버 상태</h2>
          <Badge tone={healthTone}>{healthLabel}</Badge>
        </div>
        {reachable ? (
          <dl className="ai-status-grid">
            {items.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
            <div>
              <dt>진행 중 추론</dt>
              <dd>
                {health.inflight ?? "—"}
                <span> / {health.maxInflight ?? "—"}</span>
              </dd>
              {maxInflight > 0 && (
                <Progress
                  value={Math.round((inflight / maxInflight) * 100)}
                  tone={usageTone(Math.round((inflight / maxInflight) * 100))}
                />
              )}
            </div>
          </dl>
        ) : (
          <p className="panel-note">
            AI 서버에 연결할 수 없어 모델 정보를 표시할 수 없습니다. AI 서버
            실행 상태를 확인해 주세요.
          </p>
        )}
      </section>
      <section className="metrics-grid three" aria-label="AI 성능 지표">
        {metricEntries.map(([key, metric], i) => (
          <Metric
            key={key}
            icon={<BarChart3 size={20} />}
            value={metricValue(metric)}
            label={METRIC_LABELS[key] ?? key.replaceAll("_", " ")}
            hint={metric.description ?? ""}
            change={metric.change || undefined}
            color={COLORS[i % COLORS.length]}
          />
        ))}
      </section>
      {!metricEntries.length && (
        <section className="panel pipeline-panel">
          <p className="panel-note solo">
            표시할 AI 성능 지표가 없습니다. 모델 평가 결과가 등록되면 여기에
            나타납니다.
          </p>
        </section>
      )}
      <section className="panel pipeline-panel">
        <div className="panel-heading">
          <h2>AI 파이프라인 상태</h2>
          {rows.length > 0 && (
            <span className="sample-chip">
              {live ? "AI 서버 실시간" : "등록된 값"}
            </span>
          )}
        </div>
        <div className="pipeline-list">
          {rows.map((item) => (
            <div className="pipeline-row" key={item.key}>
              <span className={item.healthy ? "green-dot" : "red-dot"} />
              <div className="pipeline-name">
                <strong>{item.name}</strong>
                {item.detail && <small>{item.detail}</small>}
              </div>
              <span className="pipeline-state">{item.statusText}</span>
              <span className="pipeline-latency">{item.latency ?? ""}</span>
              {item.usage !== undefined && item.usage !== null ? (
                <>
                  <Progress value={item.usage} tone={usageTone(item.usage)} />
                  <span>{item.usage}%</span>
                </>
              ) : (
                <>
                  <span className="pipeline-spacer" />
                  <span />
                </>
              )}
            </div>
          ))}
          {!rows.length && (
            <p className="panel-note">등록된 파이프라인 상태가 없습니다.</p>
          )}
        </div>
      </section>
      <p className="aggregate-note">
        {live
          ? "AI 서버가 알려 주는 모델 적재 상태와 진행 중 추론 수입니다. 응답 속도는 측정값이 등록되면 표시됩니다."
          : "백엔드 AI 모니터링 API에서 조회한 운영 데이터입니다"}
      </p>
    </>
  );
}
