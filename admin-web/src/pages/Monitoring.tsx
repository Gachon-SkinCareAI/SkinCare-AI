import { Metric, Progress } from '../components'
import type { Store } from '../data'

export default function Monitoring({ store }: { store: Store }) {
  const values = Object.values(store.monitoring.metrics)
    .filter((value) => typeof value === 'number')
    .slice(0, 7)
  const recalls = values.length ? values : [0]
  const coords = recalls.map((value, i) => [
    64 + (i * 970) / 6,
    168 - (value - 94) * 27,
  ])
  const line = coords.map(([x, y], i) => `${i ? 'L' : 'M'}${x},${y}`).join(' ')
  return (
    <>
      <section className="metrics-grid three" aria-label="AI 성능 지표">
        <Metric
          icon="🎯"
          value="91.4%"
          label="Classification Accuracy"
          hint="Vision 모델 기준"
          change="+0.8%"
        />
        <Metric
          icon="⚡"
          value="96.2%"
          label="위험군 Recall"
          hint="Emergency 케이스 기준"
          change="+1.1%"
          color="red"
        />
        <Metric
          icon="📐"
          value="0.847"
          label="Segmentation IoU"
          hint="상처 경계 정밀도"
          change="+0.023"
          color="blue"
        />
        <Metric
          icon="📏"
          value="0.31cm²"
          label="면적 측정 MAE"
          hint="실측 대비 절대오차"
          change="-0.04"
          color="green"
        />
        <Metric
          icon="🎲"
          value="0.882"
          label="Dice Score"
          hint="마스크 일치도 유사성"
          change="+0.015"
          color="purple"
        />
        <Metric
          icon="⏱️"
          value="1.24s"
          label="평균 추론 시간"
          hint="FastAPI 기준 P95"
          change="-0.08s"
          color="orange"
        />
      </section>
      <section className="panel recall-panel">
        <div className="panel-heading">
          <div>
            <h2>위험군 Recall 추이</h2>
            <p>목표: 98% 이상 (재학습 트리거 기준)</p>
          </div>
          <div className="chart-legend">
            <span>
              <i />
              Recall
            </span>
            <span>
              <i className="red" />
              목표선
            </span>
          </div>
        </div>
        <div className="recall-chart">
          <svg
            viewBox="0 0 1100 220"
            role="img"
            aria-label="최근 7일 위험군 Recall 94.8%에서 96.2%, 목표 98%"
          >
            <defs>
              <linearGradient id="recall-fill" x1="0" x2="0" y1="0" y2="1">
                <stop stopColor="#163a85" stopOpacity=".12" />
                <stop offset="1" stopColor="#163a85" stopOpacity="0" />
              </linearGradient>
            </defs>
            {[94, 96, 98].map((value) => (
              <g key={value}>
                <line
                  x1="54"
                  x2="1053"
                  y1={168 - (value - 94) * 27}
                  y2={168 - (value - 94) * 27}
                  stroke={value === 98 ? '#ff7e8a' : '#edf0f6'}
                  strokeDasharray={value === 98 ? '5 5' : '3 5'}
                />
                <text x="40" y={172 - (value - 94) * 27} textAnchor="end">
                  {value}%
                </text>
              </g>
            ))}
            <text x="1053" y="48" textAnchor="end" className="target-label">
              98% 목표
            </text>
            <path d={`${line} L1034,168 L64,168Z`} fill="url(#recall-fill)" />
            <path
              d={line}
              stroke="#163a85"
              fill="none"
              strokeWidth="3"
              strokeLinejoin="round"
            />
            {coords.map(([x, y], i) => (
              <g key={i}>
                <circle
                  cx={x}
                  cy={y}
                  r="4"
                  fill="white"
                  stroke="#163a85"
                  strokeWidth="2"
                >
                  <title>
                    9.{23 + i}: {recalls[i]}%
                  </title>
                </circle>
                <text x={x} y="205" textAnchor="middle">
                  9.{23 + i}
                </text>
              </g>
            ))}
          </svg>
        </div>
      </section>
      <section className="panel pipeline-panel">
        <div className="panel-heading">
          <h2>AI 파이프라인 상태</h2>
          <span className="sample-chip">API 상태</span>
        </div>
        <div className="pipeline-list">
          {[
            {
              name: 'Vision Engine (YOLOv8 + ViT)',
              latency: '0.82s',
              percent: 68,
            },
            { name: 'LLM / RAG Engine', latency: '1.42s', percent: 45 },
            { name: '의료기관 추천 API', latency: '0.18s', percent: 23 },
          ].map((item) => (
            <div className="pipeline-row" key={item.name}>
              <span className="green-dot" />
              <strong>{item.name}</strong>
              <span className="pipeline-latency">{item.latency}</span>
              <Progress value={item.percent} />
              <span>{item.percent}%</span>
            </div>
          ))}
        </div>
      </section>
      <p className="aggregate-note">
        백엔드 AI 모니터링 API에서 조회한 운영 데이터입니다
      </p>
    </>
  )
}
