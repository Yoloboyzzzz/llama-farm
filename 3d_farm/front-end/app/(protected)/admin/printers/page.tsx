"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { authenticatedFetch } from '@/lib/auth'
import { toast } from "sonner"

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

const colorMap: Record<string, string> = {
  Black: "bg-gray-800",
  White: "bg-gray-200 border border-gray-400",
  Red: "bg-red-600",
  Blue: "bg-blue-600",
  Green: "bg-green-600",
  Yellow: "bg-yellow-400",
  Orange: "bg-orange-500",
  Pink: "bg-pink-400",
  Silver: "bg-gray-400",
  Gold: "bg-yellow-500",
  Marble: "bg-stone-300",
  Transparent: "bg-slate-100 border border-gray-300",
}

function getStatus(printer: any): { label: string; color: string } {
  let status: string
  if (printer.status === "offline") status = "offline"
  else if (printer.status === "printing") status = "printing"
  else if (
    (printer.status === "idle" || printer.status === "operational") &&
    (printer.weightOfCurrentPrint > 0 || printer.weight_of_current_print > 0)
  ) status = "ready"
  else status = "idle"

  const color =
    status === "printing" ? "bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300" :
    status === "ready"    ? "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-300" :
    status === "idle"     ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300" :
                            "bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300"

  return { label: status, color }
}

// Reusable dark-mode modal input
const inputClass = "border border-gray-300 dark:border-gray-600 rounded px-3 py-2 focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 w-full"
const modalClass = "bg-white dark:bg-gray-800 rounded-lg shadow-lg w-full max-w-md p-6 border border-gray-200 dark:border-gray-700"

export default function PrintersPage() {
  const router = useRouter()
  const [user, setUser] = useState<{ name: string; email: string; role: string } | null>(null)
  const [printers, setPrinters] = useState<any[]>([])
  const [search, setSearch] = useState("")
  const [modelFilter, setModelFilter] = useState("All")
  const [sortOrder, setSortOrder] = useState("alphaAsc")
  const [showModal, setShowModal] = useState(false)
  const [editingPrinter, setEditingPrinter] = useState<any | null>(null)
  const [deletingPrinter, setDeletingPrinter] = useState<any | null>(null)
  const [newPrinter, setNewPrinter] = useState({
    name: "", ip: "", apiKey: "", model: "", material: "", color: "",
    filamentOnSpool: 0, enoughFilament: true, weightOfCurrentPrint: 0, status: "idle",
    connectionType: "",
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [errorModalPrinter, setErrorModalPrinter] = useState<any | null>(null)
  const [printerErrors, setPrinterErrors] = useState<any[]>([])
  const [errorsLoading, setErrorsLoading] = useState(false)

  // ── Tabs & Profiles ─────────────────────────────────────────────────────
  const [tab, setTab] = useState<"printers" | "profiles">("printers")
  const [profiles, setProfiles] = useState<any[]>([])
  const [profileUploading, setProfileUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleOpenErrors = async (printer: any) => {
    setErrorModalPrinter(printer)
    setErrorsLoading(true)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/errors/printer/${printer.id}`)
      if (!res.ok) throw new Error("Failed to fetch errors")
      const data = await res.json()
      setPrinterErrors(data)
    } catch (err) {
      console.error("Failed to load printer errors:", err)
      setPrinterErrors([])
    } finally {
      setErrorsLoading(false)
    }
  }

  useEffect(() => {
    const storedUser = sessionStorage.getItem("user")
    if (!storedUser) { router.push("/login"); return }
    setUser(JSON.parse(storedUser))
  }, [router])

  const isAdmin = user?.role === "ADMIN"

  const fetchPrinters = async () => {
    try {
      const userData = JSON.parse(sessionStorage.getItem("user") || "{}")
      const res = await authenticatedFetch(`${API_URL}/api/printers`, {
        headers: { Role: userData.role || "USER" },
      })
      if (!res.ok) throw new Error("Failed to fetch printers")
      const data = await res.json()
      setPrinters(data.map((p: any, i: number) => ({ ...p, name: p.name || `Printer-${i + 1}` })))
      setLastUpdated(new Date())
    } catch (err: any) {
      toast.error("Error fetching printers: " + err.message)
    }
  }

  useEffect(() => {
    const saved = localStorage.getItem("sortOrder")
    if (saved) setSortOrder(saved)
  }, [])
  useEffect(() => { localStorage.setItem("sortOrder", sortOrder) }, [sortOrder])
  useEffect(() => {
    fetchPrinters()
    const interval = setInterval(fetchPrinters, 5000)
    return () => clearInterval(interval)
  }, [])

  const filteredPrinters = useMemo(() => {
    return printers
      .filter((p) => {
        const q = search.toLowerCase()
        return (
          (p.name?.toLowerCase().includes(q) || p.model?.toLowerCase().includes(q) ||
           p.material?.toLowerCase().includes(q) || p.color?.toLowerCase().includes(q)) &&
          (modelFilter === "All" || p.model === modelFilter)
        )
      })
      .sort((a, b) => {
        const nA = a.name || "", nB = b.name || ""
        const sA = a.filamentOnSpool ?? 0, sB = b.filamentOnSpool ?? 0
        if (sortOrder === "alphaAsc") return nA.localeCompare(nB)
        if (sortOrder === "alphaDesc") return nB.localeCompare(nA)
        if (sortOrder === "asc") return sA - sB
        if (sortOrder === "desc") return sB - sA
        return 0
      })
  }, [printers, search, modelFilter, sortOrder])

  const handleUpdatePrinter = async () => {
    if (!editingPrinter) return
    setLoading(true)
    try {
      const original = printers.find((p) => p.id === editingPrinter.id) ?? {}
      const payload = { ...editingPrinter }
      for (const key of Object.keys(payload)) {
        if (payload[key] === "" || payload[key] === null || payload[key] === undefined) {
          payload[key] = (original as any)[key] ?? payload[key]
        }
      }
      const res = await authenticatedFetch(`${API_URL}/api/printers/${editingPrinter.id}`, {
        method: "PUT", body: JSON.stringify(payload),
      })
      if (!res.ok) throw new Error("Failed to update printer")
      const updated = await res.json()
      setPrinters((prev) => prev.map((p) => (p.id === updated.id ? updated : p)))
      setEditingPrinter(null)
      toast.success("Printer updated successfully")
    } catch (err: any) {
      toast.error("Error: " + err.message)
    } finally {
      setLoading(false)
    }
  }

  const handleDeletePrinter = async () => {
    if (!deletingPrinter) return
    setLoading(true)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/printers/${deletingPrinter.id}`, { method: "DELETE" })
      if (!res.ok) throw new Error("Failed to delete printer")
      setPrinters((prev) => prev.filter((p) => p.id !== deletingPrinter.id))
      setDeletingPrinter(null)
      toast.success("Printer deleted")
    } catch (err: any) {
      toast.error("Error: " + err.message)
    } finally {
      setLoading(false)
    }
  }

  const handleAddPrinter = async () => {
    const newErrors: Record<string, string> = {}
    if (!newPrinter.name) newErrors.name = "Name is required"
    if (!newPrinter.model) newErrors.model = "Model is required"
    if (!newPrinter.material) newErrors.material = "Material is required"
    if (newPrinter.weightOfCurrentPrint < 0) newErrors.weightOfCurrentPrint = "Must be ≥ 0"
    setErrors(newErrors)
    if (Object.keys(newErrors).length > 0) return
    setLoading(true)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/printers`, {
        method: "POST", body: JSON.stringify(newPrinter),
      })
      if (!res.ok) throw new Error("Failed to add printer")
      const created = await res.json()
      setPrinters((prev) => [...prev, created])
      toast.success("Printer added successfully")
      setShowModal(false)
      setNewPrinter({ name: "", ip: "", apiKey: "", model: "", material: "", color: "",
        filamentOnSpool: 0, enoughFilament: true, weightOfCurrentPrint: 0, status: "idle", connectionType: "" })
      await fetchPrinters()
    } catch (err: any) {
      toast.error("Error: " + err.message)
    } finally {
      setLoading(false)
    }
  }

  const fetchProfiles = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/printer-profiles`)
      if (!res.ok) throw new Error("Failed to fetch profiles")
      setProfiles(await res.json())
    } catch (err: any) {
      toast.error("Error fetching profiles: " + err.message)
    }
  }

  useEffect(() => { fetchProfiles() }, [])

  const handleUploadProfile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setProfileUploading(true)
    const form = new FormData()
    form.append("file", file)
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/printer-profiles/upload`, {
        method: "POST", body: form,
      })
      const text = await res.text()
      if (!res.ok) throw new Error(text)
      toast.success("Profile uploaded successfully")
      await fetchProfiles()
    } catch (err: any) {
      toast.error(err.message)
    } finally {
      setProfileUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ""
    }
  }

  const handleDeleteProfile = async (id: number) => {
    if (!confirm("Delete this printer profile?")) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/printer-profiles/${id}`, { method: "DELETE" })
      if (!res.ok) throw new Error("Failed to delete profile")
      toast.success("Profile deleted")
      setProfiles((prev) => prev.filter((p) => p.id !== id))
    } catch (err: any) {
      toast.error(err.message)
    }
  }

  if (!user) return null

  // Group profiles by printer model for display
  const profilesByModel = profiles.reduce<Record<string, any[]>>((acc, p) => {
    const key = p.printerModel ?? "Unknown"
    if (!acc[key]) acc[key] = []
    acc[key].push(p)
    return acc
  }, {})

  return (
    <section className="h-full w-full p-6 bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
      {/* Page title + tab switcher */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-semibold text-gray-800 dark:text-gray-100">Printers</h2>
        <div className="flex rounded-lg overflow-hidden border border-gray-300 dark:border-gray-600">
          {(["printers", "profiles"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-5 py-2 text-sm font-medium capitalize transition ${
                tab === t
                  ? "bg-blue-600 text-white"
                  : "bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
              }`}
            >
              {t === "printers" ? "Printers" : "Printer Profiles"}
            </button>
          ))}
        </div>
      </div>

      {/* ── PROFILES TAB ─────────────────────────────────────────────── */}
      {tab === "profiles" && (
        <div>
          {isAdmin && (
            <div className="flex items-center gap-4 mb-6">
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={profileUploading}
                className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition disabled:opacity-50"
              >
                {profileUploading ? "Uploading..." : "⬆ Upload .ini Profile"}
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".ini"
                className="hidden"
                onChange={handleUploadProfile}
              />
            </div>
          )}

          {profiles.length === 0 ? (
            <p className="text-gray-500 dark:text-gray-400 text-sm">No printer profiles found.</p>
          ) : (
            <div className="space-y-8">
              {Object.entries(profilesByModel).sort(([a], [b]) => a.localeCompare(b)).map(([model, modelProfiles]) => (
                <div key={model}>
                  <h3 className="text-lg font-semibold text-gray-700 dark:text-gray-200 mb-3 border-b border-gray-200 dark:border-gray-700 pb-1">
                    {model}
                  </h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {modelProfiles.map((profile) => (
                      <div
                        key={profile.id}
                        className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 shadow-sm flex flex-col justify-between"
                      >
                        <div className="space-y-1 text-sm text-gray-600 dark:text-gray-400">
                          <p className="text-base font-semibold text-gray-800 dark:text-gray-100 mb-2">
                            {profile.material}
                          </p>
                          <p>Plate: <span className="text-gray-800 dark:text-gray-200">
                            {profile.plateWidthMm} × {profile.plateDepthMm} × {profile.plateHeightMm} mm
                          </span></p>
                          <p className="truncate text-xs text-gray-400 dark:text-gray-500" title={profile.configPath}>
                            {profile.configPath?.split(/[\\/]/).slice(-2).join("/")}
                          </p>
                        </div>
                        {isAdmin && (
                          <button
                            onClick={() => handleDeleteProfile(profile.id)}
                            className="mt-3 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-200 text-sm font-medium text-left"
                          >
                            🗑️ Delete
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── PRINTERS TAB ─────────────────────────────────────────────── */}
      {tab === "printers" && (<>
      {/* Filters */}
      <div className="flex flex-col md:flex-row md:items-center justify-between mb-6 gap-4">
        <div className="flex items-center justify-between w-full">
          <div className="flex items-center gap-3">
          {isAdmin && (
            <button
              onClick={() => setShowModal(true)}
              className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition"
            >
              ➕ Add Printer
            </button>
          )}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <input
            type="text"
            placeholder="Search printers..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="border border-gray-300 dark:border-gray-600 rounded-md px-3 py-2 w-60 focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
          />
          <select
            value={modelFilter}
            onChange={(e) => setModelFilter(e.target.value)}
            className="border border-gray-300 dark:border-gray-600 rounded-md px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
          >
            {["All", ...new Set(printers.map((p) => p.model))].map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
          <select
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value)}
            className="border border-gray-300 dark:border-gray-600 rounded-md px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
          >
            <option value="alphaAsc">A → Z</option>
            <option value="alphaDesc">Z → A</option>
            <option value="desc">Most Filament</option>
            <option value="asc">Least Filament</option>
          </select>
        </div>
      </div>

      <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
        Last updated: {lastUpdated?.toLocaleTimeString() || "Loading..."}
      </p>

      {/* Printer Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {filteredPrinters.map((printer) => {
          const { label: status, color: statusColor } = getStatus(printer)

          return (
            <div
              key={printer.id || printer.name}
              onClick={() => handleOpenErrors(printer)}
              className="bg-white dark:bg-gray-800 rounded-lg shadow p-4 border border-gray-200 dark:border-gray-700 hover:shadow-lg transition flex flex-col justify-between cursor-pointer"
            >
              <div className="flex justify-between items-center mb-3">
                <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100">{printer.name}</h3>
                <span className={`px-2 py-1 text-xs font-semibold rounded ${statusColor}`}>
                  {status}
                </span>
              </div>

              <div className="space-y-1 text-sm text-gray-600 dark:text-gray-400">
                <p>Model: <span className="text-gray-800 dark:text-gray-200">{printer.model}</span></p>
                <p>Material: <span className="text-gray-800 dark:text-gray-200">{printer.material}</span></p>
                <div className="flex items-center gap-2">
                  <p>Color:</p>
                  <span className={`w-4 h-4 rounded-full ${colorMap[printer.color] || "bg-gray-300"}`} />
                  <span className="text-gray-800 dark:text-gray-200">{printer.color}</span>
                </div>
                <p>Filament: <span className="text-gray-800 dark:text-gray-200">{printer.filamentOnSpool}g</span></p>
                <p>Current Print: <span className="text-gray-800 dark:text-gray-200">{printer.weightOfCurrentPrint}g</span></p>
              </div>

              {/* Success / Fail mini chart */}
              {(() => {
                const success = printer.successCount ?? 0
                const fail = printer.failCount ?? 0
                const total = success + fail
                const successPct = total > 0 ? (success / total) * 100 : 0
                return (
                  <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
                    <div className="flex justify-between text-xs font-medium mb-1">
                      <span className="text-green-600 dark:text-green-400">✓ {success} success</span>
                      <span className="text-red-500 dark:text-red-400">✗ {fail} failed</span>
                    </div>
                    {total > 0 ? (
                      <>
                        <div className="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-full overflow-hidden flex">
                          <div className="bg-green-500 h-full transition-all" style={{ width: `${successPct}%` }} />
                          <div className="bg-red-500 h-full transition-all" style={{ width: `${100 - successPct}%` }} />
                        </div>
                        <p className="text-xs text-gray-400 dark:text-gray-500 mt-1 text-right">
                          {successPct.toFixed(0)}% success rate
                        </p>
                      </>
                    ) : (
                      <p className="text-xs text-gray-400 dark:text-gray-500">No prints recorded yet</p>
                    )}
                  </div>
                )
              })()}

              {isAdmin && (
                <div className="flex justify-between items-center mt-3 gap-3">
                  <button
                    onClick={async (e) => {
                      e.stopPropagation()
                      const newVal = !printer.inUse
                      try {
                        await authenticatedFetch(`${API_URL}/api/printers/${printer.id}/in-use`, {
                          method: "PUT",
                          headers: { "Content-Type": "application/json" },
                          body: JSON.stringify({ inUse: newVal }),
                        })
                        setPrinters(prev => prev.map(p => p.id === printer.id ? { ...p, inUse: newVal } : p))
                        toast.success(`${printer.name} ${newVal ? "enabled" : "disabled"}`)
                      } catch {
                        toast.error("Failed to update printer")
                      }
                    }}
                    title={printer.inUse === false ? "Printer is excluded from queue & monitoring" : "Printer is active"}
                    className={`relative w-10 h-5 rounded-full transition-colors duration-200 flex-shrink-0 ${
                      printer.inUse === false ? "bg-gray-300 dark:bg-gray-600" : "bg-green-500"
                    }`}
                  >
                    <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform duration-200 ${
                      printer.inUse === false ? "translate-x-0" : "translate-x-5"
                    }`} />
                  </button>
                  <div className="flex gap-3">
                  <button
                    onClick={(e) => { e.stopPropagation(); setEditingPrinter(printer) }}
                    className="text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-200 text-sm font-medium"
                  >
                    ✏️ Edit
                  </button>
                  <button
                    onClick={(e) => { e.stopPropagation(); setDeletingPrinter(printer) }}
                    className="text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-200 text-sm font-medium"
                  >
                    🗑️ Delete
                  </button>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {/* Edit Modal */}
      {editingPrinter && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={modalClass}>
            <h3 className="text-xl font-semibold mb-4 text-gray-800 dark:text-gray-100">
              Edit Printer: {editingPrinter.name}
            </h3>
            <div className="space-y-3 max-h-[60vh] overflow-y-auto pr-1">
              {["name","ip","apiKey","model","material","color","filamentOnSpool","weightOfCurrentPrint"].map((key) => (
                <div key={key} className="flex flex-col">
                  <label className="text-sm font-medium capitalize mb-1 text-gray-700 dark:text-gray-300">
                    {key.replace(/_/g, " ")}
                  </label>
                  <input
                    type={["filamentOnSpool","weightOfCurrentPrint"].includes(key) ? "number" : "text"}
                    value={(editingPrinter as any)[key] ?? ""}
                    onChange={(e) => setEditingPrinter({
                      ...editingPrinter,
                      [key]: ["filamentOnSpool","weightOfCurrentPrint"].includes(key)
                        ? Number(e.target.value) : e.target.value,
                    })}
                    className={inputClass}
                  />
                </div>
              ))}
              <div className="flex flex-col">
                <label className="text-sm font-medium mb-1 text-gray-700 dark:text-gray-300">Connection Type</label>
                <select
                  value={editingPrinter.connectionType ?? ""}
                  onChange={(e) => setEditingPrinter({ ...editingPrinter, connectionType: e.target.value })}
                  className={inputClass}
                >
                  <option value="">— Select —</option>
                  <option value="prusalink">PrusaLink</option>
                  <option value="octoprint">OctoPrint</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setEditingPrinter(null)}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 transition"
              >
                Cancel
              </button>
              <button
                onClick={handleUpdatePrinter}
                className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 transition"
              >
                {loading ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Modal */}
      {deletingPrinter && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={modalClass}>
            <h3 className="text-xl font-semibold mb-4 text-gray-800 dark:text-gray-100">Confirm Delete</h3>
            <p className="text-gray-700 dark:text-gray-300 mb-6">
              Are you sure you want to delete <strong>{deletingPrinter.name}</strong>?
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeletingPrinter(null)}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 transition"
              >
                Cancel
              </button>
              <button
                onClick={handleDeletePrinter}
                className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 transition"
              >
                {loading ? "Deleting..." : "Delete"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Printer Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={modalClass}>
            <h3 className="text-xl font-semibold mb-4 text-gray-800 dark:text-gray-100">Add New Printer</h3>
            <div className="space-y-3 max-h-[60vh] overflow-y-auto pr-1">
              {Object.entries(newPrinter).map(([key, value]) => {
                if (key === "status" || key === "enoughFilament") return null
                if (key === "connectionType") return (
                  <div key={key} className="flex flex-col">
                    <label className="text-sm font-medium mb-1 text-gray-700 dark:text-gray-300">Connection Type</label>
                    <select
                      value={newPrinter.connectionType}
                      onChange={(e) => setNewPrinter({ ...newPrinter, connectionType: e.target.value })}
                      className={inputClass}
                    >
                      <option value="">— Select —</option>
                      <option value="prusalink">PrusaLink</option>
                      <option value="octoprint">OctoPrint</option>
                    </select>
                    {errors.connectionType && <span className="text-red-500 text-xs mt-1">{errors.connectionType}</span>}
                  </div>
                )
                return (
                  <div key={key} className="flex flex-col">
                    <label className="text-sm font-medium capitalize mb-1 text-gray-700 dark:text-gray-300">
                      {key}
                    </label>
                    <input
                      type={typeof value === "number" ? "number" : "text"}
                      value={value as any}
                      onChange={(e) => setNewPrinter({
                        ...newPrinter,
                        [key]: typeof value === "number" ? Number(e.target.value) : e.target.value,
                      })}
                      className={inputClass}
                    />
                    {errors[key] && <span className="text-red-500 text-xs mt-1">{errors[key]}</span>}
                  </div>
                )
              })}
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 transition"
              >
                Cancel
              </button>
              <button
                onClick={handleAddPrinter}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition"
              >
                {loading ? "Adding..." : "Add"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Printer Error Log Modal */}
      {errorModalPrinter && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setErrorModalPrinter(null)}>
          <div
            className="bg-white dark:bg-gray-800 rounded-lg shadow-lg w-full max-w-2xl p-6 border border-gray-200 dark:border-gray-700 max-h-[80vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-xl font-semibold text-gray-800 dark:text-gray-100">
                Error log — <span className="text-red-500">{errorModalPrinter.name}</span>
              </h3>
              <button
                onClick={() => setErrorModalPrinter(null)}
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 text-2xl leading-none"
              >
                ×
              </button>
            </div>

            {errorsLoading ? (
              <p className="text-gray-500 dark:text-gray-400 text-sm py-8 text-center">Loading errors...</p>
            ) : printerErrors.length === 0 ? (
              <p className="text-gray-500 dark:text-gray-400 text-sm py-8 text-center">No errors recorded for this printer.</p>
            ) : (
              <div className="overflow-y-auto flex-1">
                <table className="min-w-full text-sm">
                  <thead className="sticky top-0 bg-gray-50 dark:bg-gray-700">
                    <tr>
                      <th className="px-4 py-2 text-left text-gray-600 dark:text-gray-300 font-semibold w-44">Date & Time</th>
                      <th className="px-4 py-2 text-left text-gray-600 dark:text-gray-300 font-semibold">Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    {printerErrors.map((err, i) => (
                      <tr key={err.id ?? i} className="border-t border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
                        <td className="px-4 py-2 text-gray-500 dark:text-gray-400 whitespace-nowrap">
                          {new Date(err.occurredAt).toLocaleString()}
                        </td>
                        <td className="px-4 py-2 text-gray-800 dark:text-gray-200">{err.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
      </>)}
    </section>
  )
}