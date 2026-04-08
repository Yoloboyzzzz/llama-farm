"use client"

import { useRouter } from "next/navigation"
import { useEffect, useState } from "react"

export default function AuthGuard({
  children,
  requireAdmin = false,
}: {
  children: React.ReactNode
  requireAdmin?: boolean
}) {
  const router = useRouter()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const raw = sessionStorage.getItem("user")
    if (!raw) {
      router.push("/login")
      return
    }

    const user = JSON.parse(raw)

    if (requireAdmin && user.role !== "ADMIN") {
      router.push("/")
      return
    }

    setReady(true)
  }, [requireAdmin, router])

  if (!ready) return null
  return <>{children}</>
}
