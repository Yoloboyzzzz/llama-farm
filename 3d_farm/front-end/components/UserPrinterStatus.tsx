"use client"

import { useEffect, useState } from "react"
import { authenticatedFetch } from "@/lib/auth"

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type PrinterSummary = {
  printing: number
  idle: number
  done: number
  offline: number
}

const statusConfig = {
  printing: { label: "Printing", dot: "bg-green-500" },
  done:     { label: "Done",     dot: "bg-yellow-500" },
  idle:     { label: "Idle",     dot: "bg-blue-500" },
  offline:  { label: "Offline",  dot: "bg-red-500" },
}

export default function UserPrinterStatus() {
  const [summary, setSummary] = useState<PrinterSummary | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [secondsAgo, setSecondsAgo] = useState(0)

  const fetchSummary = async () => {
    try {
      // ✅ Hits a user-scoped endpoint — no filenames, no job details
      const res = await authenticatedFetch(`${API_URL}/api/user/printers/summary`)
      if (!res.ok) throw new Error("Failed")
      const data = await res.json()
      setSummary(data)
      setLastUpdated(new Date())
      setSecondsAgo(0)
    } catch (err) {
      console.error("Failed to fetch printer summary:", err)
    }
  }

  useEffect(() => {
    fetchSummary()
    const interval = setInterval(fetchSummary, 10000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    if (!lastUpdated) return
    const t = setInterval(() => {
      setSecondsAgo(Math.floor((Date.now() - lastUpdated.getTime()) / 1000))
    }, 1000)
    return () => clearInterval(t)
  }, [lastUpdated])

  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl shadow p-6 border border-gray-200 dark:border-gray-700 transition-colors duration-200">
      <div className="flex justify-between items-start mb-6">
        <div>
          <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">
            Printer Status Overview
          </h2>
          {lastUpdated && (
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
              Last updated {secondsAgo}s ago
            </p>
          )}
        </div>
        <button
          onClick={fetchSummary}
          className="text-xs text-blue-600 dark:text-blue-400 hover:underline"
        >
          Refresh
        </button>
      </div>

      {!summary ? (
        <p className="text-gray-500 dark:text-gray-400 text-sm">Loading...</p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {(Object.keys(statusConfig) as (keyof typeof statusConfig)[]).map((key) => (
            <div
              key={key}
              className="flex flex-col items-center justify-center bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4 border border-gray-100 dark:border-gray-700"
            >
              <div className={`w-3 h-3 rounded-full mb-2 ${statusConfig[key].dot}`} />
              <span className="text-2xl font-bold text-gray-800 dark:text-gray-100">
                {summary[key]}
              </span>
              <span className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {statusConfig[key].label}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}