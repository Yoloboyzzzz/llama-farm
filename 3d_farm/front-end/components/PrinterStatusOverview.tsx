"use client"

import { useState, useMemo, useEffect, useCallback } from "react"
import { authenticatedFetch } from '@/lib/auth'
import { toast } from "sonner"
import QueueManager from "@/components/QueueManager"

type PrinterStatus = "printing" | "done" | "idle" | "offline" | "error"
export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type Printer = {
  id: number
  name?: string
  ip?: string
  api_key?: string
  model: string
  material: string
  color: string
  filamentOnSpool: number
  enough_filament?: boolean
  weightOfCurrentPrint: number
  status: string
  currentFile?: {
    id?: number
    filename?: string
    durationSeconds?: number
    startedAt?: string
    remainingTimeSeconds?: number | null
  } | null
}

type PrinterStats = {
  success: number
  fail: number
  failReasons: string[]
}

type FailLogEntry = {
  printerName: string
  reason: string
  time: string
}

type QueueItem = {
  id: number
  filename: string
  printerModel: string
  position: number
  duration: number
}

export default function PrinterStatusOverview() {
  const [selected, setSelected] = useState<PrinterStatus | null>("printing")
  const [printers, setPrinters] = useState<Record<string, Printer>>({})
  const [printerStates, setPrinterStates] = useState<Record<string, PrinterStatus>>({})
  const [printerStats, setPrinterStats] = useState<Record<string, PrinterStats>>({})
  const [modalPrinter, setModalPrinter] = useState<string | null>(null)
  const [failModalPrinter, setFailModalPrinter] = useState<string | null>(null)
  const [failReason, setFailReason] = useState<string>("Z-Homing")
  const [loadingPrinter, setLoadingPrinter] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [secondsAgo, setSecondsAgo] = useState(0)

  const [queue, setQueue] = useState<QueueItem[]>([])
  const [queueOpen, setQueueOpen] = useState(false)
  const [requeueModal, setRequeueModal] = useState<{ fileId: number; filename: string } | null>(null)
  const [failLog, setFailLog] = useState<FailLogEntry[]>([])
  const [modalLoading, setModalLoading] = useState(false)
  const [failModalLoading, setFailModalLoading] = useState(false)

  const truncateFilename = (name: string, max = 40) =>
    name.length > max ? name.slice(0, max) + "…" : name

  const failReasons = [
    { key: "Z-Homing", label: "Z-Homing" },
    { key: "Layer Shift", label: "Layer Shift" },
    { key: "Human Erro", label: "Human Error" },
    { key: "Out Of Filament", label: "Out Of Filament" },
    { key: "Other", label: "Other" },
  ]

  useEffect(() => {
    if (!lastUpdated) return
    const interval = setInterval(() => {
      setSecondsAgo(Math.floor((Date.now() - lastUpdated.getTime()) / 1000))
    }, 1000)
    return () => clearInterval(interval)
  }, [lastUpdated])

  const fetchPrinters = useCallback(async () => {
    try {
      const userData = sessionStorage.getItem("user") || localStorage.getItem("user")
      const parsed = userData ? JSON.parse(userData) : null
      const role = parsed?.role || "USER"

      const res = await authenticatedFetch(`${API_URL}/api/printers/status`, {
        headers: { Role: role },
      })

      if (!res.ok) throw new Error("Failed to fetch printers")
      const data = await res.json()

      const normalized: Record<string, Printer> = {}
      data.forEach((printer: any, i: number) => {
        const name = printer.name || `Printer-${i + 1}`
        normalized[name] = printer
      })

      setPrinters(normalized)
      setLastUpdated(new Date())
    } catch (err) {
      console.error("Error fetching printers:", err)
    }
  }, [])

  useEffect(() => {
    fetchPrinters()
    const interval = setInterval(fetchPrinters, 1000)
    return () => clearInterval(interval)
  }, [fetchPrinters])

  useEffect(() => {
    const fetchQueueCount = async () => {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/queue`)
        const data = await res.json()
        setQueue(Array.isArray(data) ? data : [])
      } catch (err) {
        console.error("Failed to fetch queue count:", err)
      }
    }

    fetchQueueCount()
    const interval = setInterval(fetchQueueCount, 1000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    const timer = setInterval(() => setPrinters((prev) => ({ ...prev })), 1000)
    return () => clearInterval(timer)
  }, [])

  const categorized = useMemo(() => {
    const printing: string[] = []
    const done: string[] = []
    const idle: string[] = []
    const offline: string[] = []
    const error: string[] = []
    const newStates: Record<string, PrinterStatus> = {}

    Object.entries(printers).forEach(([name, printer]) => {
      let status: PrinterStatus

      if (printer.status === "offline") status = "offline"
      else if (printer.status === "error") status = "error"
      else if (printer.status === "printing" && printer.weightOfCurrentPrint > 0) status = "printing"
      else if (
        (printer.status === "operational" || printer.status === "idle") &&
        printer.weightOfCurrentPrint > 0
      ) status = "done"
      else status = "idle"

      newStates[name] = status
      if (!printerStats[name]) printerStats[name] = { success: 0, fail: 0, failReasons: [] }

      switch (status) {
        case "printing": printing.push(name); break
        case "done": done.push(name); break
        case "idle": idle.push(name); break
        case "offline": offline.push(name); break
        case "error": error.push(name); break
      }
    })

    setPrinterStates(newStates)
    printing.sort((a, b) => a.localeCompare(b))
    done.sort((a, b) => a.localeCompare(b))
    idle.sort((a, b) => a.localeCompare(b))
    offline.sort((a, b) => a.localeCompare(b))
    error.sort((a, b) => a.localeCompare(b))

    return { printing, done, idle, offline, error }
  }, [printers])

  const handleClick = (status: PrinterStatus) => {
    setQueueOpen(false)
    setSelected((prev) => (prev === status ? null : status))
  }

  const handleAbortPrint = async (name: string) => {
    if (!confirm(`Are you sure you want to abort the print on ${name}?`)) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/printers/${name}/abort`, {
        method: "POST",
      })
      if (!res.ok) throw new Error("Failed to abort print")
      toast.success(`Print on ${name} aborted`)
      setPrinterStates((prev) => ({ ...prev, [name]: "idle" }))
    } catch (err) {
      console.error("Error aborting print:", err)
      toast.error("Failed to abort print")
    }
  }

  const handleConfirm = async (name: string, success: boolean) => {
    if (success) {
      setModalLoading(true)
      try {
        const res = await authenticatedFetch(`${API_URL}/api/printers/${printers[name].id}/set-idle`, {
          method: "POST",
        })
        if (!res.ok) throw new Error("Failed to reset printer weight")
      } catch (err) {
        console.error("Error resetting printer weight:", err)
        setModalLoading(false)
        return
      }
      setPrinterStats((prev) => ({
        ...prev,
        [name]: { ...prev[name], success: prev[name].success + 1 },
      }))
      await fetchPrinters()
      setModalLoading(false)
      setModalPrinter(null)
    } else {
      setModalPrinter(null)
      setFailModalPrinter(name)
      setFailReason(failReasons[0].key)
    }
  }

  const handleFailConfirm = async (name: string) => {
    setFailModalLoading(true)
    try {
      const res = await authenticatedFetch(
        `${API_URL}/api/printers/${encodeURIComponent(name)}/report-fail?reason=${encodeURIComponent(failReason)}`,
        { method: "POST" }
      )
      if (!res.ok) throw new Error(await res.text())
    } catch (err) {
      console.error("Failed to report print failure:", err)
      toast.error("Failed to record print failure on the server")
      setFailModalLoading(false)
      return
    }

    const reasonLabel = failReasons.find((r) => r.key === failReason)?.label ?? failReason
    setFailLog((prev) => [
      { printerName: name, reason: reasonLabel, time: new Date().toLocaleTimeString() },
      ...prev,
    ])
    setPrinterStats((prev) => ({
      ...prev,
      [name]: {
        ...prev[name],
        fail: prev[name].fail + 1,
        failReasons: [...prev[name].failReasons, failReason],
      },
    }))
    const fileId = printers[name]?.currentFile?.id
    const filename = printers[name]?.currentFile?.filename ?? "this file"
    await fetchPrinters()
    setFailModalLoading(false)
    setFailModalPrinter(null)
    if (fileId) setRequeueModal({ fileId, filename })
  }

  const handleRequeue = async () => {
    if (!requeueModal) return
    const { fileId, filename } = requeueModal
    setRequeueModal(null)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/gcode-files/${fileId}/requeue`, { method: "POST" })
      if (!res.ok) throw new Error(await res.text())
      toast.success(`${filename} requeued`)
    } catch (err) {
      toast.error(`Failed to requeue: ${err}`)
    }
  }

  const handleSetOnline = async (name: string) => {
    setLoadingPrinter(name)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/printers/${name}/set-online`, {
        method: "POST",
      })
      if (!res.ok) throw new Error("Failed to set printer online")
      setPrinterStates((prev) => ({ ...prev, [name]: "idle" }))
    } catch (err) {
      console.error(`Error setting ${name} online:`, err)
      toast.error(`Failed to set ${name} online`)
    } finally {
      setLoadingPrinter(null)
    }
  }

  return (
    <div className="bg-white dark:bg-gray-800 p-6 rounded-2xl shadow-md w-full transition-colors duration-200">
      <h2 className="text-2xl font-semibold text-gray-800 dark:text-gray-100 mb-1">
        Printer Status Overview
      </h2>

      {lastUpdated && (
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          Last updated {secondsAgo} seconds ago
        </p>
      )}

      {/* Status Summary */}
      <div className="grid grid-cols-6 gap-6 mb-4">
        {([
          { label: "Printing", color: "bg-green-500", key: "printing" },
          { label: "Done", color: "bg-yellow-500", key: "done" },
          { label: "Idle", color: "bg-blue-500", key: "idle" },
          { label: "Error", color: "bg-orange-500", key: "error" },
          { label: "Offline", color: "bg-red-500", key: "offline" },
        ] as const).map((item) => (
          <div
            key={item.key}
            onClick={() => handleClick(item.key)}
            className={`cursor-pointer rounded-xl p-4 text-center transition transform hover:scale-105 hover:bg-gray-50 dark:hover:bg-gray-700 ${
              selected === item.key ? "ring-4 ring-blue-400 bg-gray-50 dark:bg-gray-700" : ""
            }`}
          >
            <div className={`${item.color} w-4 h-4 rounded-full mx-auto mb-2`} />
            <p className="text-gray-700 dark:text-gray-300 font-medium">{item.label}</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {categorized[item.key].length}
            </p>
          </div>
        ))}

        <div
          onClick={() => { setSelected(null); setQueueOpen((prev) => !prev) }}
          className={`cursor-pointer rounded-xl p-4 text-center transition transform hover:scale-105 hover:bg-gray-50 dark:hover:bg-gray-700 ${
            queueOpen ? "ring-4 ring-blue-400 bg-gray-50 dark:bg-gray-700" : ""
          }`}
        >
          <div className="bg-purple-500 w-4 h-4 rounded-full mx-auto mb-2" />
          <p className="text-gray-700 dark:text-gray-300 font-medium">Queue</p>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">{queue.length}</p>
        </div>
      </div>

      {/* Printer List */}
      {selected && (
        <div className="mt-4 border-t border-gray-200 dark:border-gray-700 pt-4">
          <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">
            {selected.charAt(0).toUpperCase() + selected.slice(1)} Printers
          </h3>

          {selected === "printing" ? (
            <div className="overflow-x-auto">
              <table className="min-w-full border border-gray-200 dark:border-gray-700 rounded-lg">
                <thead className="bg-gray-100 dark:bg-gray-700">
                  <tr>
                    {["Printer Name", "Filename", "Time Remaining", "Filament Remaining", "Action"].map(h => (
                      <th key={h} className="px-4 py-2 text-left text-gray-700 dark:text-gray-300 font-semibold">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {categorized.printing.length > 0 ? (
                    categorized.printing.map((name) => {
                      const printer = printers[name]
                      const file = printer?.currentFile || {}
                      const filename = file?.filename || "-"

                      let timeRemaining = "-"
                      const formatSeconds = (remaining: number) => {
                        const days = Math.floor(remaining / 86400)
                        const hours = Math.floor((remaining % 86400) / 3600)
                        const minutes = Math.floor((remaining % 3600) / 60)
                        const seconds = Math.floor(remaining % 60)
                        const parts = []
                        if (days > 0) parts.push(`${days}d`)
                        if (hours > 0) parts.push(`${hours}h`)
                        if (minutes > 0) parts.push(`${minutes}m`)
                        if (seconds > 0) parts.push(`${seconds}s`)
                        return parts.length > 0 ? parts.join(" ") : "0s"
                      }
                      if (file?.remainingTimeSeconds != null) {
                        timeRemaining = formatSeconds(Math.max(file.remainingTimeSeconds, 0))
                      } else if (file?.durationSeconds && file?.startedAt) {
                        const startedAt = new Date(file.startedAt).getTime()
                        const elapsed = (Date.now() - startedAt) / 1000
                        const remaining = Math.max(file.durationSeconds + 300 - elapsed, 0)
                        timeRemaining = formatSeconds(remaining)
                      }

                      const filamentRemainingValue =
                        printer.filamentOnSpool != null && printer.weightOfCurrentPrint != null
                          ? printer.filamentOnSpool - printer.weightOfCurrentPrint
                          : null

                      const filamentRemainingText =
                        filamentRemainingValue != null ? `${filamentRemainingValue.toFixed(0)} g` : "-"

                      const filamentColorClass =
                        filamentRemainingValue != null
                          ? filamentRemainingValue < 0
                            ? "text-red-500 font-semibold"
                            : "text-green-500 font-semibold"
                          : "text-gray-700 dark:text-gray-300"

                      return (
                        <tr key={name} className="border-t border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                          <td className="px-4 py-2 font-medium text-gray-800 dark:text-gray-200">{name}</td>
                          <td className="px-4 py-2 text-gray-700 dark:text-gray-300">
                            <span title={filename !== "-" ? filename : undefined}>
                              {truncateFilename(filename)}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-gray-700 dark:text-gray-300">{timeRemaining}</td>
                          <td className={`px-4 py-2 ${filamentColorClass}`}>{filamentRemainingText}</td>
                          <td className="px-4 py-2 text-center">
                            <button
                              onClick={() => handleAbortPrint(name)}
                              className="bg-red-500 text-white px-3 py-1 rounded-md text-sm hover:bg-red-600 transition"
                            >
                              🚫 Abort
                            </button>
                          </td>
                        </tr>
                      )
                    })
                  ) : (
                    <tr>
                      <td colSpan={5} className="px-4 py-4 text-center text-gray-500 dark:text-gray-400">
                        No active prints
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

          ) : selected === "done" ? (
            <div className="overflow-x-auto">
              <table className="min-w-full border border-gray-200 dark:border-gray-700 rounded-lg">
                <thead className="bg-gray-100 dark:bg-gray-700">
                  <tr>
                    {["Printer Name", "Filename", "Action"].map(h => (
                      <th key={h} className="px-4 py-2 text-left text-gray-700 dark:text-gray-300 font-semibold">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {categorized.done.length > 0 ? (
                    categorized.done.map((name) => {
                      const printer = printers[name]
                      const filename = printer?.currentFile?.filename || "-"
                      return (
                        <tr key={name} className="border-t border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                          <td className="px-4 py-2 font-medium text-gray-800 dark:text-gray-200">{name}</td>
                          <td className="px-4 py-2 text-gray-700 dark:text-gray-300">
                            <span title={filename !== "-" ? filename : undefined}>
                              {truncateFilename(filename)}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-center">
                            <button
                              onClick={() => setModalPrinter(name)}
                              className="bg-blue-500 text-white px-3 py-1 rounded-md text-sm hover:bg-blue-600 transition"
                            >
                              Set Idle
                            </button>
                          </td>
                        </tr>
                      )
                    })
                  ) : (
                    <tr>
                      <td colSpan={3} className="px-4 py-4 text-center text-gray-500 dark:text-gray-400">
                        No done printers
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

          ) : (
            <ul className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
              {categorized[selected].length > 0 ? (
                categorized[selected].map((name) => (
                  <li
                    key={name}
                    className="border border-gray-200 dark:border-gray-700 rounded-md px-3 py-2 bg-gray-50 dark:bg-gray-700 text-sm text-gray-800 dark:text-gray-200 flex flex-col gap-1"
                  >
                    <div className="flex justify-between items-center">
                      <span className="font-medium">{name}</span>
                      {selected === "offline" && (
                        <button
                          onClick={() => handleSetOnline(name)}
                          disabled={loadingPrinter === name}
                          className={`${
                            loadingPrinter === name
                              ? "bg-gray-400 cursor-not-allowed"
                              : "bg-green-500 hover:bg-green-600"
                          } text-white px-2 py-1 rounded text-xs transition`}
                        >
                          {loadingPrinter === name ? "Connecting..." : "🔄 Set Online"}
                        </button>
                      )}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-gray-400">
                      {printers[name]?.model} • {printers[name]?.material} • {printers[name]?.color}
                    </div>
                  </li>
                ))
              ) : (
                <p className="text-gray-500 dark:text-gray-400 text-sm col-span-full">
                  There are no {selected} printers 🥳
                </p>
              )}
            </ul>
          )}
        </div>
      )}

      {/* Success Modal */}
      {modalPrinter && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 p-6 rounded-xl shadow-lg w-80 border border-gray-200 dark:border-gray-700">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 text-center">
              Was the print on <span className="text-blue-500">{modalPrinter}</span> successful?
            </h3>
            <div className="flex justify-between gap-3 mt-4">
              <button
                onClick={() => handleConfirm(modalPrinter, true)}
                disabled={modalLoading}
                className="flex-1 bg-green-500 text-white py-2 rounded hover:bg-green-600 transition disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {modalLoading ? "Saving…" : "✅ Yes"}
              </button>
              <button
                onClick={() => handleConfirm(modalPrinter, false)}
                disabled={modalLoading}
                className="flex-1 bg-red-500 text-white py-2 rounded hover:bg-red-600 transition disabled:opacity-60 disabled:cursor-not-allowed"
              >
                ❌ No
              </button>
            </div>
            <button
              onClick={() => setModalPrinter(null)}
              disabled={modalLoading}
              className="block text-sm text-gray-500 dark:text-gray-400 mt-4 mx-auto hover:text-gray-700 dark:hover:text-gray-200 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Inline Queue */}
      {queueOpen && (
        <div className="mt-4 border-t border-gray-200 dark:border-gray-700 pt-4">
          <QueueManager />
        </div>
      )}

      {/* Fail Log Table */}
      {failLog.length > 0 && (
        <div className="mt-6 border-t border-gray-200 dark:border-gray-700 pt-4">
          <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3">
            Print Failures
          </h3>
          <div className="overflow-x-auto">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 rounded-lg">
              <thead className="bg-gray-100 dark:bg-gray-700">
                <tr>
                  {["Printer Name", "Fail Reason", "Time"].map((h) => (
                    <th key={h} className="px-4 py-2 text-left text-gray-700 dark:text-gray-300 font-semibold">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {failLog.map((entry, i) => (
                  <tr key={i} className="border-t border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                    <td className="px-4 py-2 font-medium text-gray-800 dark:text-gray-200">{entry.printerName}</td>
                    <td className="px-4 py-2 text-red-600 dark:text-red-400">{entry.reason}</td>
                    <td className="px-4 py-2 text-gray-500 dark:text-gray-400 text-sm">{entry.time}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Failure Modal */}
      {failModalPrinter && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 p-6 rounded-xl shadow-lg w-96 border border-gray-200 dark:border-gray-700">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 text-center">
              What was the issue with <span className="text-red-500">{failModalPrinter}</span>?
            </h3>
            <label className="block text-sm text-gray-600 dark:text-gray-400 mb-2">
              Select issue type:
            </label>
            <select
              className="w-full border border-gray-300 dark:border-gray-600 rounded-md p-2 text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700"
              value={failReason}
              onChange={(e) => setFailReason(e.target.value)}
            >
              {failReasons.map((reason) => (
                <option key={reason.key} value={reason.key}>
                  {reason.label}
                </option>
              ))}
            </select>
            <div className="flex justify-between gap-3 mt-5">
              <button
                onClick={() => handleFailConfirm(failModalPrinter)}
                disabled={failModalLoading}
                className="flex-1 bg-blue-500 text-white py-2 rounded hover:bg-blue-600 transition disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {failModalLoading ? "Saving…" : "Confirm"}
              </button>
              <button
                onClick={() => setFailModalPrinter(null)}
                disabled={failModalLoading}
                className="flex-1 bg-gray-200 dark:bg-gray-600 text-gray-800 dark:text-gray-200 py-2 rounded hover:bg-gray-300 dark:hover:bg-gray-500 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Requeue Modal */}
      {requeueModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 p-6 rounded-xl shadow-lg w-96 border border-gray-200 dark:border-gray-700">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-2 text-center">
              Requeue this file?
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 text-center mb-5 truncate">
              {requeueModal.filename}
            </p>
            <div className="flex justify-between gap-3">
              <button
                onClick={handleRequeue}
                className="flex-1 bg-blue-500 text-white py-2 rounded hover:bg-blue-600 transition"
              >
                ✅ Yes, requeue
              </button>
              <button
                onClick={() => setRequeueModal(null)}
                className="flex-1 bg-gray-200 dark:bg-gray-600 text-gray-800 dark:text-gray-200 py-2 rounded hover:bg-gray-300 dark:hover:bg-gray-500 transition"
              >
                No
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
