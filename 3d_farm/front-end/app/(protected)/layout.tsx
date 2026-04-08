"use client"

import { useEffect, useState } from "react"
import { useRouter, usePathname } from "next/navigation"
import { authenticatedFetch } from "@/lib/auth"

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

export default function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/auth/me`, {
          cache: 'no-store',
        })

        if (!res.ok) {
          router.replace(`/login?redirect=${encodeURIComponent(pathname)}`)
        } else {
          setChecking(false)
        }
      } catch (error) {
        console.error('Auth check failed:', error)
        router.replace('/login')
      }
    }

    checkAuth()
  }, [pathname])

  if (checking) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
        <div className="text-center">
          <div className="text-6xl mb-4 animate-bounce">🦙</div>
          <p className="text-gray-600 dark:text-gray-400 text-lg">Loading...</p>
        </div>
      </div>
    )
  }

  return <>{children}</>
}