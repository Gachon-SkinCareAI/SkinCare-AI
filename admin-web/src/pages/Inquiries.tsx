import { useState } from 'react'
import {
  Badge,
  Empty,
  Pagination,
  SearchInput,
  Tabs,
  usePagination,
} from '../components'
import { filterInquiries, type OpenDetail, type Store } from '../data'

export default function Inquiries({
  store,
  open,
}: {
  store: Store
  open: OpenDetail
}) {
  const [filter, setFilter] = useState('전체')
  const [query, setQuery] = useState('')
  const filtered = filterInquiries(store.inquiries, filter, query)
  const pagination = usePagination(filtered)
  return (
    <>
      <div className="inquiry-tabs">
        <Tabs
          label="문의 분류"
          items={[
            '전체',
            '답변 대기',
            '답변 완료',
            'AI 분석 결과 문의',
            'AI 분석 정확도 문의',
            '기타',
          ]}
          value={filter}
          onChange={(v) => {
            setFilter(v)
            pagination.setPage(1)
          }}
        />
      </div>
      <section className="panel table-panel">
        <div className="panel-heading inquiry-heading">
          <div>
            <h2>사용자 문의 목록</h2>
            <p>AI 분석과 서비스 이용에 관한 문의를 확인하고 답변합니다.</p>
          </div>
          <SearchInput
            label="문의 검색"
            placeholder="문의 검색"
            value={query}
            onChange={(v) => {
              setQuery(v)
              pagination.setPage(1)
            }}
          />
        </div>
        {filtered.length ? (
          <div className="table-scroll">
            <table className="inquiry-table">
              <thead>
                <tr>
                  <th>문의 번호</th>
                  <th>문의 유형</th>
                  <th>사용자</th>
                  <th>문의 내용</th>
                  <th>관련 분석 기록</th>
                  <th>등록일</th>
                  <th>상태</th>
                  <th>상세보기</th>
                </tr>
              </thead>
              <tbody>
                {pagination.visible.map((q) => {
                  const diagnosis = store.diagnoses.find(
                    (d) => d.id === q.diagnosisId,
                  )
                  return (
                    <tr key={q.id}>
                      <td>
                        <strong>{q.id}</strong>
                      </td>
                      <td>{q.category}</td>
                      <td>{q.author}</td>
                      <td>
                        <span className="truncate" title={q.subject}>
                          {q.subject}
                        </span>
                      </td>
                      <td>
                        {diagnosis ? (
                          <button
                            className="subtle-link"
                            onClick={() =>
                              open({ type: 'diagnosis', id: diagnosis.id })
                            }
                          >
                            {diagnosis.area} · {diagnosis.condition}
                          </button>
                        ) : (
                          <span className="muted">-</span>
                        )}
                      </td>
                      <td className="date-cell">
                        {q.date.replaceAll('-', '.')}
                      </td>
                      <td>
                        <Badge tone={q.reply.trim() ? 'green' : 'orange'}>
                          {q.reply.trim() ? '답변 완료' : '답변 대기'}
                        </Badge>
                      </td>
                      <td>
                        <button
                          className="table-link"
                          aria-label={`문의 ${q.id} 상세보기`}
                          onClick={() => open({ type: 'inquiry', id: q.id })}
                        >
                          상세보기
                        </button>
                      </td>
                    </tr>
                  )
                })}
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
