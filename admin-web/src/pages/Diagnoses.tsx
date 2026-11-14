import { useState } from 'react'
import { Download } from 'lucide-react'
import {
  Badge,
  Empty,
  Pagination,
  SearchInput,
  Tabs,
  usePagination,
} from '../components'
import {
  analysisStatusLabel,
  downloadCsv,
  filterAnalyses,
  formatTime,
  type Store,
} from '../data'

const STATUS_TABS = ['전체', '문진 진행 중', '결과 저장 완료', '재연결 필요', '생성됨']

function statusTone(status: string) {
  return status === 'COMPLETED'
    ? 'green'
    : status === 'RETRY_REQUIRED'
      ? 'orange'
      : status === 'QUESTIONNAIRE'
        ? 'blue'
        : 'gray'
}

// 앱의 실제 분석 기록을 운영 정보(기록 번호·처리 상태·문진 횟수·시각)만으로 보여 준다.
// 분석 사진·문진 내용·분석 결과와 사용자 정보는 개인정보 처리방침에 따라 관리자 화면에 표시하지 않는다.
export default function Diagnoses({ store }: { store: Store }) {
  const [status, setStatus] = useState('전체')
  const [query, setQuery] = useState('')
  const filtered = filterAnalyses(store.analyses, query, status)
  const pagination = usePagination(filtered, 10)
  return (
    <>
      <div className="log-toolbar">
        <Tabs
          label="분석 처리 상태 필터"
          items={STATUS_TABS}
          value={status}
          onChange={(v) => {
            setStatus(v)
            pagination.setPage(1)
          }}
        />
        <div className="toolbar-actions">
          <SearchInput
            label="분석 기록 검색"
            placeholder="기록 번호 검색"
            value={query}
            onChange={(v) => {
              setQuery(v)
              pagination.setPage(1)
            }}
          />
          <button
            className="icon-button"
            aria-label="현재 분석 기록 목록 CSV 다운로드"
            onClick={() =>
              downloadCsv('analyses.csv', [
                ['기록 번호', '생성 시각', '처리 상태', '문진 횟수', '마지막 갱신'],
                ...filtered.map((a) => [
                  a.id,
                  formatTime(a.createdAt),
                  analysisStatusLabel[a.status] || a.status,
                  a.turn,
                  formatTime(a.updatedAt),
                ]),
              ])
            }
          >
            <Download size={17} />
          </button>
        </div>
      </div>
      <section className="panel table-panel" aria-label="분석 기록 목록">
        {filtered.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>기록 번호</th>
                  <th>생성 시각</th>
                  <th>처리 상태</th>
                  <th>문진 횟수</th>
                  <th>마지막 갱신</th>
                </tr>
              </thead>
              <tbody>
                {pagination.visible.map((a) => (
                  <tr key={a.id}>
                    <td className="mono">{a.id}</td>
                    <td className="muted">{formatTime(a.createdAt)}</td>
                    <td>
                      <Badge tone={statusTone(a.status)}>
                        {analysisStatusLabel[a.status] || a.status}
                      </Badge>
                      {a.aiUnavailable && (
                        <small className="muted"> · AI 연결 확인 필요</small>
                      )}
                    </td>
                    <td>{a.turn}회</td>
                    <td className="muted">{formatTime(a.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty />
        )}
        <Pagination
          count={filtered.length}
          {...pagination}
          onChange={pagination.setPage}
        />
      </section>
      <p className="aggregate-note">
        운영 확인용 기록 번호와 처리 상태만 표시합니다. 분석 사진·문진 내용·분석 결과와
        사용자 정보는 개인정보 처리방침에 따라 관리자 화면에 표시하지 않습니다.
      </p>
    </>
  )
}
