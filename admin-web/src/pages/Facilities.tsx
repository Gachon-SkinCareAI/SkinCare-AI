import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
import {
  Badge,
  Empty,
  Metric,
  Pagination,
  SearchInput,
  usePagination,
} from '../components'
import type { OpenDetail, Store } from '../data'

export default function Facilities({
  store,
  open,
}: {
  store: Store
  open: OpenDetail
}) {
  const m = store.dashboard
  const [query, setQuery] = useState('')
  const [type, setType] = useState('전체')
  const filtered = store.facilities.filter(
    (f) =>
      `${f.name} ${f.region}`
        .toLocaleLowerCase()
        .includes(query.trim().toLocaleLowerCase()) &&
      (type === '전체' || f.type === type),
  )
  const pagination = usePagination(filtered)
  return (
    <>
      <section className="metrics-grid three" aria-label="의료기관 지표">
        <Metric
          icon="🏥"
          value={String(m.activeHospitals ?? store.facilities.length)}
          label="등록 의료기관"
          hint="피부과 · 병원 · 약국 포함"
        />
        <Metric
          icon="🌙"
          value={String(store.facilities.filter((f) => f.night).length)}
          label="야간진료 가능"
          hint="22시 이후 운영"
          color="purple"
        />
        <Metric
          icon="💊"
          value={String(store.facilities.filter((f) => f.type.includes('PHARMACY') || f.type.includes('약국')).length)}
          label="공공심야약국"
          hint="전국 기준"
          color="green"
        />
      </section>
      <section className="panel table-panel">
        <div className="panel-heading facility-heading">
          <div>
            <h2>의료기관 목록</h2>
            <p>
              백엔드 의료기관 데이터
            </p>
          </div>
          <button
            className="button primary"
            onClick={() => open({ type: 'sync' })}
          >
            <RefreshCw size={16} />
            OpenAPI 데이터 동기화
          </button>
        </div>
        <div className="table-filters">
          <SearchInput
            label="의료기관 검색"
            placeholder="기관명 또는 지역 검색"
            value={query}
            onChange={(v) => {
              setQuery(v)
              pagination.setPage(1)
            }}
          />
          <label className="select-field">
            <span className="sr-only">의료기관 종류</span>
            <select
              aria-label="의료기관 종류"
              value={type}
              onChange={(e) => {
                setType(e.target.value)
                pagination.setPage(1)
              }}
            >
              <option value="전체">모든 종류</option>
              <option>피부과</option>
              <option>종합병원</option>
              <option>약국</option>
            </select>
          </label>
        </div>
        {filtered.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>기관명</th>
                  <th>종류</th>
                  <th>지역</th>
                  <th>야간진료</th>
                  <th>응급실</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {pagination.visible.map((f) => (
                  <tr key={f.id}>
                    <td>
                      <button
                        className="table-link navy-link"
                        aria-label={`${f.name} 상세 보기`}
                        onClick={() => open({ type: 'facility', id: f.id })}
                      >
                        {f.name}
                      </button>
                    </td>
                    <td>{f.type}</td>
                    <td>{f.region}</td>
                    <td>
                      <Badge tone={f.night ? 'purple' : 'gray'}>
                        {f.night ? '가능' : '-'}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={f.emergency ? 'red' : 'gray'}>
                        {f.emergency ? '있음' : '-'}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={f.active ? 'green' : 'gray'}>
                        {f.active ? '활성' : '비활성'}
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
          unit="개 기관"
          onChange={pagination.setPage}
        />
      </section>
    </>
  )
}
