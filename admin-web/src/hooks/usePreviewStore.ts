import { useAdminStore } from './useAdminStore'
import type { Store } from '../data'

// 기존 저장소 호환용 API 로더
export function usePreviewStore(enabled = true) {
  const { store, setStore, reload, loading, error } = useAdminStore(enabled)
  return {
    store,
    setStore: (next: Store) => setStore(next),
    storageError: Boolean(error),
    loading,
    error,
    reload,
  }
}
