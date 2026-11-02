import { useState } from 'react'
import { Download } from 'lucide-react'
import {
  Badge,
  Empty,
  Pagination,
  Progress,
  RiskBadge,
  SearchInput,
  Tabs,
  usePagination,
} from '../components'
import {
  downloadCsv,
  filterDiagnoses,
  type OpenDetail,
  type Risk,
  type Store,
} from '../data'

export default function Diagnoses({
  store,
  open,
  initialRisk,
}: {
  store: Store
  open: OpenDetail
  initialRisk?: Risk
}) {
  const [risk, setRisk] = useState(initialRisk || '전체')
  const [query, setQuery] = useState('')
  const filtered = filterDiagnoses(store.diagnoses, query, risk)
  const pagination = usePagination(filtered, 6)
  return (
    <>
      <div className="log-toolbar">
        <Tabs
          label="진단 위험도 필터"
          items={['전체', '응급', '경고', '관찰']}
          value={risk}
          onChange={(v) => {
            setRisk(v)
            pagination.setPage(1)
          }}
        />
        <div className="toolbar-actions">
          <SearchInput
            label="진단 검색"
            placeholder="진단 ID 검색"
            value={query}
            onChange={(v) => {
              setQuery(v)
              pagination.setPage(1)
            }}
          />
          <button
            className="icon-button"
            aria-label="현재 진단 목록 CSV 다운로드"
            onClick={() =>
              downloadCsv('diagnoses.csv', [
                [
                  '진단 ID',
                  '사용자',
                  '시간',
                  '의심 유형',
                  '부위',
                  '위험도',
                  '신뢰도',
                  '상태',
                ],
                ...filtered.map((d) => [
                  d.id,
                  d.userId,
                  d.time,
                  d.condition,
                  d.area,
                  d.risk,
                  d.confidence,
                  d.status,
                ]),
              ])
            }
          >
            <Download size={17} />
          </button>
        </div>
      </div>
      <section className="panel table-panel" aria-label="진단 로그 목록">
        {filtered.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>진단 ID</th>
                  <th>사용자</th>
                  <th>시간</th>
                  <th>의심 유형</th>
                  <th>부위</th>
                  <th>위험도</th>
                  <th>신뢰도</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {pagination.visible.map((d) => (
                  <tr key={d.id}>
                    <td>
                      <button
                        className="table-link"
                        aria-label={`${d.id} 진단 상세`}
                        onClick={() => open({ type: 'diagnosis', id: d.id })}
                      >
                        {d.id}
                      </button>
                    </td>
                    <td>
                      <button
                        className="subtle-link"
                        onClick={() => open({ type: 'user', id: d.userId })}
                      >
                        {d.userId}
                      </button>
                    </td>
                    <td className="muted">{d.time}</td>
                    <td>{d.condition}</td>
                    <td>{d.area}</td>
                    <td>
                      <RiskBadge risk={d.risk} />
                    </td>
                    <td>
                      <span className="confidence">
                        <Progress value={d.confidence} />
                        {d.confidence}%
                      </span>
                    </td>
                    <td>
                      <Badge
                        tone={
                          d.status === '진행중'
                            ? 'blue'
                            : d.status === '알림발송'
                              ? 'orange'
                              : 'green'
                        }
                      >
                        {d.status}
                      </Badge>
                    </td>
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
    </>
  )
}
