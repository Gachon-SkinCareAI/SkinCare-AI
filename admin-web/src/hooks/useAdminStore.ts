import { useCallback, useEffect, useState } from 'react'
import { getAdminStore } from '../services/adminApi'
import type { Store } from '../data'

// 관리자 화면 데이터를 API에서 로드
export function useAdminStore(enabled = true) {
  const [store, setStore] = useState<Store | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await getAdminStore()
      setStore({
        version: 3,
        ...data,
      })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '데이터를 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!enabled) return
    void reload()
  }, [enabled, reload])

  return { store, setStore, loading, error, reload }
}
