// 관리자 인증 응답 타입
export type AdminSession = {
  adminId: string
  name: string
  token: string
}

// 공통 페이지 응답 타입
export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  hasNext: boolean
  total: number
}
