"use client"

import { useState, useMemo, useEffect } from "react"
import { authenticatedFetch } from '@/lib/auth'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts"

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

export default function FilamentUsageChart() {
  const [period, setPeriod] = useState<"week" | "month" | "year">("week")
  const [completedPrints, setCompletedPrints] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [isDark, setIsDark] = useState(false)

  // Detect dark mode
  useEffect(() => {
    const check = () => setIsDark(document.documentElement.classList.contains("dark"))
    check()
    const observer = new MutationObserver(check)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
    return () => observer.disconnect()
  }, [])

  // Load real data from backend
  useEffect(() => {
    async function load() {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/filament-usage`)
        const data = await res.json()
        setCompletedPrints(data)
      } catch (err) {
        console.error("Failed to load filament usage:", err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const data = useMemo(() => {
    if (loading) return []

    const now = new Date()
    const filtered = completedPrints.filter((p) => {
      const completed = new Date(p.completedAt)
      const diffDays = (now.getTime() - completed.getTime()) / (1000 * 60 * 60 * 24)

      if (period === "week") return diffDays <= 7
      if (period === "month") return diffDays <= 30
      if (period === "year") return diffDays <= 365
      return false
    })

    const map = new Map<string, number>()

    filtered.forEach((p) => {
      const d = new Date(p.completedAt)

      let key: string
      if (period === "week") {
        key = d.toISOString().split("T")[0]
      } else if (period === "month") {
        const firstDayOfYear = new Date(d.getFullYear(), 0, 1)
        const weekNumber = Math.ceil(((+d - +firstDayOfYear) / 86400000 + firstDayOfYear.getDay() + 1) / 7)
        key = `${d.getFullYear()}-W${weekNumber}`
      } else {
        key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`
      }

      map.set(key, (map.get(key) || 0) + p.filamentUsedGrams)
    })

    const arr = Array.from(map, ([key, grams]) => {
      let displayDate = key
      if (period === "week") {
        displayDate = new Date(key).toLocaleDateString("en-US", { month: "short", day: "numeric" })
      } else if (period === "month") {
        displayDate = `Week ${key.split("-W")[1]}`
      } else {
        const [year, month] = key.split("-")
        displayDate = new Date(Number(year), Number(month) - 1).toLocaleString("en-US", { month: "short" })
      }

      return { key, displayDate, grams }
    })

    arr.sort((a, b) => {
      if (period === "month") {
        const [yearA, wA] = a.key.split("-W")
        const [yearB, wB] = b.key.split("-W")
        return Number(yearA) - Number(yearB) || Number(wA) - Number(wB)
      }
      return a.key.localeCompare(b.key)
    })
    return arr
  }, [period, completedPrints, loading])

  // Dark mode chart colors
  const chartColors = {
    grid: isDark ? "#374151" : "#e5e7eb",
    axis: isDark ? "#9ca3af" : "#6b7280",
    bar: isDark ? "#3b82f6" : "#2563eb",
    tooltip: {
      bg: isDark ? "#1f2937" : "#ffffff",
      border: isDark ? "#374151" : "#e5e7eb",
      text: isDark ? "#f9fafb" : "#111827",
    },
  }

  if (loading) {
    return (
      <div className="bg-white dark:bg-gray-800 p-6 rounded-2xl shadow-md w-full h-full flex items-center justify-center transition-colors">
        <p className="text-gray-500 dark:text-gray-400">Loading chart...</p>
      </div>
    )
  }

  return (
    <div className="bg-white dark:bg-gray-800 p-6 rounded-2xl shadow-md w-full h-full flex flex-col transition-colors duration-200">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-semibold text-gray-800 dark:text-gray-100">
          Filament Usage Overview
        </h2>

        <div className="flex gap-3">
          {(["week", "month", "year"] as const).map((p) => (
            <button
              key={p}
              onClick={() => setPeriod(p)}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
                period === p
                  ? "bg-blue-600 text-white"
                  : "bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600"
              }`}
            >
              {p.charAt(0).toUpperCase() + p.slice(1)}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 min-h-[300px]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 20, right: 40, left: 20, bottom: 20 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} />
            <XAxis
              dataKey="displayDate"
              tick={{ fill: chartColors.axis }}
              axisLine={{ stroke: chartColors.grid }}
            />
            <YAxis
              label={{
                value: "Filament (g)",
                angle: -90,
                position: "insideLeft",
                fill: chartColors.axis,
              }}
              tick={{ fill: chartColors.axis }}
              axisLine={{ stroke: chartColors.grid }}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: chartColors.tooltip.bg,
                border: `1px solid ${chartColors.tooltip.border}`,
                borderRadius: "8px",
                color: chartColors.tooltip.text,
              }}
            />
            <Bar
              dataKey="grams"
              fill={chartColors.bar}
              radius={[6, 6, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}