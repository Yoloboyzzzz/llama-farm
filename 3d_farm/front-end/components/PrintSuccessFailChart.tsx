"use client"

import { useState, useEffect } from "react"
import { authenticatedFetch } from "@/lib/auth"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts"

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type PrinterStat = {
  name: string
  success: number
  fail: number
}

export default function PrintSuccessFailChart() {
  const [data, setData] = useState<PrinterStat[]>([])
  const [loading, setLoading] = useState(true)
  const [isDark, setIsDark] = useState(false)

  useEffect(() => {
    const check = () => setIsDark(document.documentElement.classList.contains("dark"))
    check()
    const observer = new MutationObserver(check)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    async function load() {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/printers/status`)
        const printers = await res.json()
        const stats: PrinterStat[] = printers.map((p: any) => ({
          name: p.name ?? "Unknown",
          success: p.successCount ?? 0,
          fail: p.failCount ?? 0,
        }))
        setData(stats)
      } catch (err) {
        console.error("Failed to load printer stats:", err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const chartColors = {
    grid: isDark ? "#374151" : "#e5e7eb",
    axis: isDark ? "#9ca3af" : "#6b7280",
    success: isDark ? "#22c55e" : "#16a34a",
    fail: isDark ? "#f87171" : "#dc2626",
    tooltip: {
      bg: isDark ? "#1f2937" : "#ffffff",
      border: isDark ? "#374151" : "#e5e7eb",
      text: isDark ? "#f9fafb" : "#111827",
    },
  }

  const total = data.reduce((acc, p) => ({ success: acc.success + p.success, fail: acc.fail + p.fail }), { success: 0, fail: 0 })

  if (loading) {
    return (
      <div className="bg-white dark:bg-gray-800 p-6 rounded-2xl shadow-md w-full h-full flex items-center justify-center transition-colors">
        <p className="text-gray-500 dark:text-gray-400">Loading stats...</p>
      </div>
    )
  }

  return (
    <div className="bg-white dark:bg-gray-800 p-6 rounded-2xl shadow-md w-full h-full flex flex-col transition-colors duration-200">
      <div className="flex justify-between items-start mb-4">
        <h2 className="text-2xl font-semibold text-gray-800 dark:text-gray-100">
          Print Success / Fail
        </h2>
        <div className="flex gap-4 text-sm font-medium">
          <span className="text-green-600 dark:text-green-400">{total.success} success</span>
          <span className="text-red-600 dark:text-red-400">{total.fail} failed</span>
        </div>
      </div>

      <div className="flex-1 min-h-[260px]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 20 }} barCategoryGap="30%">
            <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} />
            <XAxis
              dataKey="name"
              tick={{ fill: chartColors.axis, fontSize: 12 }}
              axisLine={{ stroke: chartColors.grid }}
              interval={0}
              angle={-20}
              textAnchor="end"
              height={50}
            />
            <YAxis
              allowDecimals={false}
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
            <Legend
              wrapperStyle={{ color: isDark ? "#d1d5db" : "#374151", fontSize: 13 }}
            />
            <Bar dataKey="success" name="Success" fill={chartColors.success} radius={[4, 4, 0, 0]} />
            <Bar dataKey="fail" name="Failed" fill={chartColors.fail} radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
