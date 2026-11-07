import { useEffect, useState } from 'react'
import { createSeed, parseStore, STORAGE_KEY } from '../data'

function loadStore() {
  try {
    return parseStore(localStorage.getItem(STORAGE_KEY))
  } catch {
    return createSeed()
  }
}

export function usePreviewStore() {
  const [store, setStore] = useState(loadStore)
  const [storageError, setStorageError] = useState(false)
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
      setStorageError(false)
    } catch {
      setStorageError(true)
    }
  }, [store])
  return { store, setStore, storageError }
}
