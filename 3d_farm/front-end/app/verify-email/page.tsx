"use client"

import { useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"

const API_URL = process.env.NEXT_PUBLIC_API_URL

type Status = "loading" | "success" | "error"

export default function VerifyEmailPage() {
  const searchParams = useSearchParams()
  const [status, setStatus] = useState<Status>("loading")

  useEffect(() => {
    const token = searchParams.get("token")
    if (!token) {
      setStatus("error")
      return
    }

    fetch(`${API_URL}/api/auth/verify?token=${encodeURIComponent(token)}`)
      .then((res) => {
        if (res.ok) {
          setStatus("success")
        } else {
          setStatus("error")
        }
      })
      .catch(() => setStatus("error"))
  }, [searchParams])

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="bg-white p-8 rounded-lg shadow-md w-full max-w-sm text-center">
        {status === "loading" && (
          <>
            <div className="text-5xl mb-4 animate-bounce">🦙</div>
            <p className="text-gray-600">Verifying your email...</p>
          </>
        )}

        {status === "success" && (
          <>
            <div className="text-5xl mb-4">✅</div>
            <h2 className="text-2xl font-semibold mb-2 text-gray-800">Email verified!</h2>
            <p className="text-gray-600 text-sm mb-4">
              Your account is now active. You can log in.
            </p>
            <a
              href="/login"
              className="inline-block bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 transition text-sm"
            >
              Go to login
            </a>
          </>
        )}

        {status === "error" && (
          <>
            <div className="text-5xl mb-4">❌</div>
            <h2 className="text-2xl font-semibold mb-2 text-gray-800">Invalid link</h2>
            <p className="text-gray-600 text-sm mb-4">
              This verification link is invalid or has already been used.
            </p>
            <a
              href="/register"
              className="text-blue-600 hover:underline text-sm"
            >
              Register again
            </a>
          </>
        )}
      </div>
    </div>
  )
}
