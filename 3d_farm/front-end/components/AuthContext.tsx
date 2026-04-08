"use client"

import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { getCurrentUser, logout as logoutFn, User } from '@/lib/auth'

type AuthContextType = {
  user: User | null
  loading: boolean
  refreshUser: () => Promise<void>
  logout: () => Promise<void>
  isAdmin: boolean
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined)
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const refreshUser = async () => {
    setLoading(true)
    const currentUser = await getCurrentUser()
    setUser(currentUser)
    setLoading(false)
  }

  const logout = async () => {
    await logoutFn()
    setUser(null)
  }

  useEffect(() => {
    refreshUser()
  }, [])

  const isAdmin = user?.role === 'ADMIN'

  return (
    <AuthContext.Provider value={{ user, loading, refreshUser, logout, isAdmin }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}