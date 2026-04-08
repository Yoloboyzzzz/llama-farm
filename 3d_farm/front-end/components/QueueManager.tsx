"use client"

import { useEffect, useState } from "react"
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core"
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { authenticatedFetch } from "@/lib/auth"
import { toast } from "sonner"

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type QueueItem = {
  id: number
  filename: string
  status: string
  printerModel: string
  position: number
  duration: number
}

type PrinterOption = {
  id: number
  name: string
  model: string
  color: string
  material: string
}

function formatDuration(seconds: number): string {
  if (!seconds) return "-"
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}

function SortableRow({
  item,
  onStartNow,
  onAbort,
  busy,
}: {
  item: QueueItem
  onStartNow: (id: number) => void
  onAbort: (id: number) => void
  busy: boolean
}) {
  const isSending = item.status === "sending"
  const { attributes, listeners, setNodeRef, transform, transition } =
    useSortable({ id: item.id, disabled: isSending })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  }

  return (
    <tr
      ref={setNodeRef}
      style={style}
      className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
    >
      <td
        className={`px-4 py-2 text-gray-400 dark:text-gray-500 ${isSending ? "cursor-not-allowed opacity-30" : "cursor-grab"}`}
        {...(!isSending ? { ...attributes, ...listeners } : {})}
      >
        ⣿
      </td>
      <td className="px-4 py-2 text-gray-700 dark:text-gray-300">{item.position}</td>
      <td className="px-4 py-2 font-medium text-gray-800 dark:text-gray-200">
        <span title={item.filename.length > 30 ? item.filename : undefined}>
          {item.filename.length > 30 ? item.filename.slice(0, 30) + "…" : item.filename}
        </span>
      </td>
      <td className="px-4 py-2 flex gap-2 items-center">
        {isSending ? (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-md bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 text-sm font-medium">
            <svg className="animate-spin h-3.5 w-3.5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z" />
            </svg>
            Sending…
          </span>
        ) : (
          <>
            <button
              onClick={(e) => { e.stopPropagation(); onStartNow(item.id) }}
              disabled={busy}
              className="bg-green-500 hover:bg-green-600 disabled:opacity-50 text-white text-sm font-medium px-3 py-1 rounded-md transition"
            >
              ▶ Start Now
            </button>
            <button
              onClick={(e) => { e.stopPropagation(); onAbort(item.id) }}
              disabled={busy}
              className="bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white text-sm font-medium px-3 py-1 rounded-md transition"
            >
              ⛔ Abort
            </button>
          </>
        )}
      </td>
      <td className="px-4 py-2 text-gray-700 dark:text-gray-300">{item.printerModel || "-"}</td>
      <td className="px-4 py-2 text-gray-700 dark:text-gray-300">{formatDuration(item.duration)}</td>
    </tr>
  )
}

export default function QueueManager({ onClose }: { onClose?: () => void }) {
  const [queue, setQueue] = useState<QueueItem[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  const [modal, setModal] = useState<{ item: QueueItem } | null>(null)
  const [compatiblePrinters, setCompatiblePrinters] = useState<PrinterOption[]>([])
  const [loadingPrinters, setLoadingPrinters] = useState(false)
  const [selectedPrinterId, setSelectedPrinterId] = useState<number | null>(null)

  const sensors = useSensors(useSensor(PointerSensor))

  const fetchQueue = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/queue`)
      const data = await res.json()
      setQueue(data)
      setLastUpdated(new Date())
      setLoading(false)
    } catch (error) {
      console.error("Failed to fetch queue:", error)
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchQueue()
    const interval = setInterval(fetchQueue, 30000)
    return () => clearInterval(interval)
  }, [])

  const handleStartNow = async (id: number) => {
    const item = queue.find((q) => q.id === id)
    if (!item) return
    setSelectedPrinterId(null)
    setCompatiblePrinters([])
    setModal({ item })
    setLoadingPrinters(true)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/printers/compatible/${id}`)
      const data = await res.json()
      setCompatiblePrinters(data)
    } catch {
      setCompatiblePrinters([])
    } finally {
      setLoadingPrinters(false)
    }
  }

  const confirmStart = async () => {
    if (!modal) return
    const { item } = modal
    setModal(null)
    setBusyId(item.id)
    try {
      const url = selectedPrinterId
        ? `${API_URL}/api/printers/start-now/${item.id}?printerId=${selectedPrinterId}`
        : `${API_URL}/api/printers/start-now/${item.id}`
      const res = await authenticatedFetch(url, { method: "POST" })
      if (!res.ok) throw new Error("Failed to start print")
      toast.success(`Print started: ${item.filename}`)
      await fetchQueue()
    } catch (err) {
      console.error("Error starting print:", err)
      toast.error("Failed to start print")
    } finally {
      setBusyId(null)
    }
  }

  const handleAbort = async (id: number) => {
    const item = queue.find((q) => q.id === id)
    if (!item) return
    if (!confirm(`Abort "${item.filename}"?`)) return
    setBusyId(id)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/queue/abort/${id}`, { method: "POST" })
      if (!res.ok) throw new Error("Failed to abort job")
      toast.success(`${item.filename} aborted`)
      await fetchQueue()
    } catch (err) {
      console.error("Abort failed:", err)
      toast.error("Failed to abort job")
    } finally {
      setBusyId(null)
    }
  }

  const handleDragEnd = async (event: any) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    // Don't allow moving a sending item or swapping with one
    const activeItem = queue.find((q) => q.id === active.id)
    const overItem = queue.find((q) => q.id === over.id)
    if (activeItem?.status === "sending" || overItem?.status === "sending") return
    const oldIndex = queue.findIndex((q) => q.id === active.id)
    const newIndex = queue.findIndex((q) => q.id === over.id)
    const newOrder = arrayMove(queue, oldIndex, newIndex)
    const updated = newOrder.map((q, idx) => ({ ...q, position: idx + 1 }))
    setQueue(updated)
    await authenticatedFetch(`${API_URL}/api/queue/reorder`, {
      method: "PUT",
      body: JSON.stringify(updated.map((q) => q.id)),
    })
  }

  return (
    <div className="p-6 bg-gray-50 dark:bg-gray-900 h-full w-full transition-colors duration-200">
      <div className="flex justify-between items-center mb-4">
        <div className="flex items-center gap-4">
          {lastUpdated && (
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Last updated: {lastUpdated.toLocaleTimeString()}
              <span className="ml-2 text-xs text-gray-400 dark:text-gray-500">(auto-refreshes every 30s)</span>
            </p>
          )}
          {onClose && (
            <button
              onClick={onClose}
              className="text-gray-500 hover:text-gray-800 dark:hover:text-gray-200 text-xl font-bold transition"
              aria-label="Close"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="text-gray-500 dark:text-gray-400">Loading queue...</div>
      ) : (
        <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-xl shadow transition-colors duration-200">
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={queue.map((q) => q.id)} strategy={verticalListSortingStrategy}>
              <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700 text-sm">
                <thead className="bg-gray-100 dark:bg-gray-700">
                  <tr>
                    <th className="px-4 py-2" />
                    {["Order", "Filename", "Action", "Printer Model", "Duration"].map((h) => (
                      <th key={h} className="px-4 py-2 text-left font-semibold text-gray-700 dark:text-gray-300">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                  {queue.length > 0 ? (
                    queue.map((item) => (
                      <SortableRow
                        key={item.id}
                        item={item}
                        onStartNow={handleStartNow}
                        onAbort={handleAbort}
                        busy={busyId === item.id}
                      />
                    ))
                  ) : (
                    <tr>
                      <td colSpan={6} className="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                        Queue is empty 🦙
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </SortableContext>
          </DndContext>
        </div>
      )}

      {/* Printer-picker modal */}
      {modal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 w-full max-w-md">
            <h2 className="text-lg font-bold text-gray-800 dark:text-gray-100 mb-1">Start Print</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4 truncate">{modal.item.filename}</p>

            {loadingPrinters ? (
              <div className="text-sm text-gray-500 dark:text-gray-400 py-6 text-center">
                Loading compatible printers...
              </div>
            ) : (
              <div className="space-y-2 mb-6">
                <label className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-600 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                  <input
                    type="radio"
                    name="printer"
                    checked={selectedPrinterId === null}
                    onChange={() => setSelectedPrinterId(null)}
                    className="accent-green-500"
                  />
                  <div>
                    <div className="font-medium text-gray-800 dark:text-gray-200 text-sm">Auto-pick</div>
                    <div className="text-xs text-gray-400 dark:text-gray-500">
                      {compatiblePrinters.length > 0
                        ? `${compatiblePrinters.length} compatible printer${compatiblePrinters.length !== 1 ? "s" : ""} available`
                        : "No compatible printers available"}
                    </div>
                  </div>
                </label>

                {compatiblePrinters.map((p) => (
                  <label
                    key={p.id}
                    className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-600 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                  >
                    <input
                      type="radio"
                      name="printer"
                      checked={selectedPrinterId === p.id}
                      onChange={() => setSelectedPrinterId(p.id)}
                      className="accent-green-500"
                    />
                    <div>
                      <div className="font-medium text-gray-800 dark:text-gray-200 text-sm">{p.name}</div>
                      <div className="text-xs text-gray-400 dark:text-gray-500">
                        {p.model} · {p.color} · {p.material}
                      </div>
                    </div>
                  </label>
                ))}

                {compatiblePrinters.length === 0 && (
                  <p className="text-xs text-amber-500 dark:text-amber-400 mt-1">
                    ⚠ No compatible printers are currently free. Auto-pick will fail.
                  </p>
                )}
              </div>
            )}

            <div className="flex justify-end gap-2">
              <button
                onClick={() => setModal(null)}
                className="px-4 py-2 text-sm rounded-md border border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={confirmStart}
                disabled={loadingPrinters}
                className="px-4 py-2 text-sm rounded-md bg-green-500 hover:bg-green-600 disabled:opacity-50 text-white font-medium transition-colors"
              >
                ▶ Start
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
