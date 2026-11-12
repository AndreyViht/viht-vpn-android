import { createContext, useContext } from 'react'

export const AdminFetchContext = createContext(null)

export function useAdminFetch() {
  return useContext(AdminFetchContext)
}
