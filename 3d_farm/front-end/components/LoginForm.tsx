"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"

export default function LoginPage() {
  const router = useRouter()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [message, setMessage] = useState("")

  const handleLogin = async () => {
    const res = await fetch("http://192.168.133.223:8080/api/users/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    })

    const data = await res.json()
    if (data.success) {
      // 🔐 Store user info in sessionStorage
      sessionStorage.setItem("user", JSON.stringify(data))
      router.push("/")
    } else {
      setMessage(data.message)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center h-screen">
      <div className="w-80 bg-white shadow p-6 rounded">
        <h2 className="text-xl mb-4 font-semibold">Login</h2>
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full mb-3 border px-3 py-2 rounded"
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full mb-3 border px-3 py-2 rounded"
        />
        <button
          onClick={handleLogin}
          className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700"
        >
          Login
        </button>
        {message && <p className="text-red-500 mt-2">{message}</p>}
      </div>
    </div>
  )
}
