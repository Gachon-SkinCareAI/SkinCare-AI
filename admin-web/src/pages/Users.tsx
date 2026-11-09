import { useState } from 'react'
import {
  Badge,
  Empty,
  Metric,
  Pagination,
  RiskBadge,
  SearchInput,
  usePagination,
} from '../components'
import type { OpenDetail, Store } from '../data'

export default function Users({
  store,
  open,
}: {
  store: Store
  open: OpenDetail
}) {
  const m = store.dashboard
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('전체')
  const filtered = store.users.filter(
    (u) =>
      `${u.id} ${u.name}`
        .toLocaleLowerCase()
        .includes(query.trim().toLocaleLowerCase()) &&
      (status === '전체' || u.active === (status === '활성')),
  )
  const pagination = usePagination(filtered)
  return (
    <>
      <section className="metrics-grid four" aria-label="사용자 지표">
        <Metric
          icon="👤"
          value={String(m.totalUsers ?? store.users.length)}
          label="총 사용자"
          hint="누적 가입자"
        />
        <Metric
          icon="⭐"
          value={String(m.premium ?? store.users.filter((u) => u.plan === 'Premium').length)}
          label="Premium 사용자"
          hint="전환율 18.2%"
          change="+3.1%"
          color="orange"
        />
        <Metric
          icon="🗓️"
          value={String(m.dau ?? 0)}
          label="DAU"
          hint="일간 활성 사용자"
          change="+5.4%"
          color="blue"
        />
        <Metric
          icon="🔄"
          value={`${m.retention ?? 0}%`}
          label="7일 재방문율"
          hint="목표: 70%"
          change="+2.1%"
          color="green"
        />
      </section>
      <section className="panel table-panel">
        <div className="panel-heading user-heading">
          <h2>사용자 목록</h2>
          <div className="toolbar-actions">
            <label className="select-field">
              <select
                aria-label="사용자 상태"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value)
                  pagination.setPage(1)
                }}
              >
                <option value="전체">모든 상태</option>
                <option>활성</option>
                <option>비활성</option>
              </select>
            </label>
            <SearchInput
              label="사용자 검색"
              placeholder="사용자 검색"
              value={query}
              onChange={(v) => {
                setQuery(v)
                pagination.setPage(1)
              }}
            />
          </div>
        </div>
        {filtered.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>사용자 ID</th>
                  <th>닉네임</th>
                  <th>가입일</th>
                  <th>총 진단</th>
                  <th>최근 위험도</th>
                  <th>플랜</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {pagination.visible.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <button
                        className="table-link"
                        aria-label={`${u.id} 사용자 상세`}
                        onClick={() => open({ type: 'user', id: u.id })}
                      >
                        {u.id}
                      </button>
                    </td>
                    <td>
                      <div className="person">
                        <span className="avatar">{u.name.slice(0, 1)}</span>
                        <span>{u.name}</span>
                      </div>
                    </td>
                    <td className="muted">{u.joinedAt.replaceAll('-', '.')}</td>
                    <td>{u.diagnoses}건</td>
                    <td>
                      <RiskBadge risk={u.risk} />
                    </td>
                    <td>
                      <Badge tone={u.plan === 'Premium' ? 'orange' : 'gray'}>
                        {u.plan}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={u.active ? 'green' : 'gray'}>
                        {u.active ? '활성' : '비활성'}
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
          unit="명"
          onChange={pagination.setPage}
        />
      </section>
    </>
  )
}
