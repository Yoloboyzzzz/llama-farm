"use client"

import { createContext, useContext } from "react"

export type UserRole = "ADMIN" | "USER" | null

export type AuthUser = {
  email: string
  role: UserRole
  isAdmin: boolean
}

export const AuthContext = createContext<AuthUser>({
  email: "",
  role: null,
  isAdmin: false,
})

export function useAuth() {
  return useContext(AuthContext)
}