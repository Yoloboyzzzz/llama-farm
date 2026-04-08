"use client"

import { useEffect, useState } from "react"
import { useRouter, usePathname } from "next/navigation"
import { authenticatedFetch } from "@/lib/auth"
import { AuthContext, AuthUser } from "@/lib/AuthContext"
import UserSidebar from "@components/UserSideBar"
import UserHeader from "@/components/UserHeader"


const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

export default function UserLayoutClient({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const [checking, setChecking] = useState(true)
  const [authUser, setAuthUser] = useState<AuthUser>({ email: "", role: null, isAdmin: false })
  const [sidebarOpen, setSidebarOpen] = useState(false)

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/auth/me`, { cache: "no-store" })

        if (!res.ok) {
          router.replace(`/login?redirect=${encodeURIComponent(pathname)}`)
          return
        }

        const data = await res.json()
        const isAdmin = data.role === "ADMIN"

        // Admins who land on /user/* get redirected to admin view
        if (isAdmin) {
          router.replace("/admin/dashboard")
          return
        }

        setAuthUser({ email: data.email, role: "USER", isAdmin: false })
        setChecking(false)
      } catch {
        router.replace("/login")
      }
    }

    checkAuth()
  }, [pathname])

  if (checking) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="text-center">
          <div className="text-6xl mb-4 animate-bounce">🦙</div>
          <p className="text-gray-600 dark:text-gray-400 text-lg">Loading...</p>
        </div>
      </div>
    )
  }

  return (
    <AuthContext.Provider value={authUser}>
      <div className="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
        <UserSidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        {sidebarOpen && (
          <div
            className="fixed inset-0 z-30 bg-black/50 md:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}
        <div className="flex flex-col flex-1 overflow-hidden min-w-0">
          <UserHeader onMenuToggle={() => setSidebarOpen((o) => !o)} />
          <main className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
            {children}
          </main>
        </div>
      </div>
    </AuthContext.Provider>
  )
}