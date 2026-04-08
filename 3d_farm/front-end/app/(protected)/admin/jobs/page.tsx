"use client"

import { useEffect, useState, Fragment } from "react"
import { authenticatedFetch } from '@/lib/auth'
import { toast } from "sonner"

export const API_URL = process.env.NEXT_PUBLIC_API_URL

function FormatDate({ iso }: { iso: string | null }) {
  if (!iso) return <>-</>
  return <>{new Date(iso).toLocaleString()}</>
}

function formatDuration(seconds: number): string {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const parts = []
  if (d) parts.push(`${d}d`)
  if (h) parts.push(`${h}h`)
  if (m) parts.push(`${m}m`)
  if (s) parts.push(`${s}s`)
  return parts.length > 0 ? parts.join(" ") : "0s"
}

type GcodeFile = {
  id: number
  filename: string
  status: string
  startedAt: string | null
  remainingTimeSeconds: number | null
  durationSeconds: number
  downloadUrl: string
}

type Job = {
  id: number
  name: string
  status: string
  createdAt: string
  userName: string
  userEmail: string
  gcodeFiles: GcodeFile[]
}

export default function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedJob, setExpandedJob] = useState<number | null>(null)
  const [search, setSearch] = useState("")
  const [renameModal, setRenameModal] = useState<{ jobId: number; currentName: string } | null>(null)
  const [renameInput, setRenameInput] = useState("")

  const fetchJobs = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/jobs/latest?limit=200`)
      if (!res.ok) throw new Error(await res.text())
      setJobs(await res.json())
    } catch (err) {
      console.error("❌ Failed to load jobs:", err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchJobs() }, [])
  
  // Refresh data every 30 seconds
  useEffect(() => {
    const interval = setInterval(() => fetchJobs(), 30000)
    return () => clearInterval(interval)
  }, [])

  const toggleExpand = (jobId: number) =>
    setExpandedJob((prev) => (prev === jobId ? null : jobId))

  const handleRequeue = async (fileId: number, filename: string) => {
    if (!confirm(`Requeue "${filename}"?\n\nThis will move it to the end of the queue and reset its status.`)) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/gcode-files/${fileId}/requeue`, { method: "POST" })
      const msg = await res.text()
      if (!res.ok) throw new Error(msg)
      toast.success(msg)
      await fetchJobs()
    } catch (err) { toast.error(`Failed to requeue: ${err}`) }
  }

  const handleDownloadStls = async (gcodeId: number) => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/jobs/gcode/${gcodeId}/stl-files/download`)
      if (!res.ok) throw new Error("Failed to download STL files")
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `stl-files-gcode-${gcodeId}.zip`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) { toast.error(`Failed to download STL files: ${err}`) }
  }

  const handleDeleteGcodeFile = async (fileId: number, filename: string) => {
    if (!confirm(`Delete G-code file "${filename}"?`)) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/gcode-files/${fileId}`, { method: "DELETE" })
      if (!res.ok) throw new Error(await res.text())
      toast.success(`G-code file "${filename}" deleted`)
      await fetchJobs()
    } catch (err) { toast.error(`Failed to delete G-code file: ${err}`) }
  }

  const openRenameModal = (jobId: number, currentName: string) => {
    setRenameInput(currentName)
    setRenameModal({ jobId, currentName })
  }

  const handleRename = async () => {
    if (!renameModal) return
    const newName = renameInput.trim()
    if (!newName) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/jobs/${renameModal.jobId}/rename`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: newName }),
      })
      if (!res.ok) throw new Error(await res.text())
      toast.success(`Job renamed to "${newName}"`)
      setRenameModal(null)
      await fetchJobs()
    } catch (err) { toast.error(`Failed to rename: ${err}`) }
  }

  const handleDeleteJob = async (jobId: number, jobName: string) => {
    if (!confirm(`Delete job "${jobName}"?`)) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/jobs/${jobId}`, { method: "DELETE" })
      if (!res.ok) throw new Error(await res.text())
      toast.success(`Job "${jobName}" deleted`)
      setJobs((prev) => prev.filter((job) => job.id !== jobId))
      if (expandedJob === jobId) setExpandedJob(null)
    } catch (err) { toast.error(`Failed to delete job: ${err}`) }
  }

  const fileStatusClass = (status: string) => {
    if (status === "printing")  return "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300"
    if (status === "completed") return "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300"
    if (status === "error")     return "bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300"
    return "bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300"
  }

  return (
    <div className="p-6 w-full h-full bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-3">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">Latest Jobs</h1>
        <input
          type="text"
          placeholder="Search job name..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="border border-gray-300 dark:border-gray-600 rounded-lg px-4 py-2 text-sm text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 w-full sm:w-72 focus:outline-none focus:ring-2 focus:ring-blue-400"
        />
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-md overflow-hidden transition-colors duration-200">
        {loading ? (
          <div className="p-6 text-center text-gray-500 dark:text-gray-400">Loading jobs...</div>
        ) : jobs.length === 0 ? (
          <div className="p-6 text-center text-gray-500 dark:text-gray-400">No jobs found.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-700">
                <tr>
                  {[
                    { label: "Job Name", align: "text-left" },
                    { label: "Status", align: "text-left" },
                    { label: "Created By", align: "text-left" },
                    { label: "Created At", align: "text-left" },
                    { label: "Files", align: "text-right" },
                    { label: "Actions", align: "text-center" },
                  ].map(({ label, align }) => (
                    <th key={label} className={`px-4 py-3 font-semibold text-gray-700 dark:text-gray-300 ${align}`}>
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>

              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {jobs.filter((job) => job.name.toLowerCase().includes(search.toLowerCase())).map((job) => {
                  const files = job.gcodeFiles ?? []
                  const hasFiles = files.length > 0
                  const allCompleted = hasFiles && files.every((f) => f.status === "completed")
                  const hasFailed = files.some((f) => f.status === "error" || f.status === "aborted" || f.status === "failed")
                  const hasActive = files.some((f) => f.status === "printing" || f.status === "queued")

                  const jobBadge = !hasFiles
                    ? { label: "Empty Job", icon: "📭", cls: "bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400" }
                    : allCompleted
                    ? { label: "Completed", icon: "✅", cls: "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300" }
                    : hasFailed && !hasActive
                    ? { label: "Finished with Failures", icon: "⚠️", cls: "bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300" }
                    : hasFailed
                    ? { label: "In Production (failures)", icon: "🔶", cls: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-300" }
                    : { label: "In Production", icon: "⚙️", cls: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300" }

                  return (
                    <Fragment key={job.id}>
                      {/* Job Row */}
                      <tr
                        className="hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition-colors"
                        onClick={() => toggleExpand(job.id)}
                      >
                        <td className="px-4 py-3 font-medium text-gray-800 dark:text-gray-200">
                          <span className="inline-flex items-center gap-2">
                            {job.name}
                            <button
                              onClick={(e) => { e.stopPropagation(); openRenameModal(job.id, job.name) }}
                              className="text-gray-400 hover:text-blue-500 transition-colors"
                              title="Rename job"
                            >
                              ✏️
                            </button>
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${jobBadge.cls}`}>
                            {jobBadge.icon} {jobBadge.label}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{job.userName || "Unknown"}</td>
                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400"><FormatDate iso={job.createdAt} /></td>
                        <td className="px-4 py-3 text-right text-gray-700 dark:text-gray-300">{job.gcodeFiles?.length || 0}</td>
                        <td className="px-4 py-3 text-center">
                          <button
                            onClick={(e) => { e.stopPropagation(); handleDeleteJob(job.id, job.name) }}
                            className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded-md text-sm transition"
                          >
                            🗑️ Delete
                          </button>
                        </td>
                      </tr>

                      {/* Expanded Row */}
                      {expandedJob === job.id && (
                        <tr className="bg-gray-50 dark:bg-gray-900/50">
                          <td colSpan={6} className="px-4 py-4">
                            <div className="border-l-4 border-blue-500 pl-4">
                              {job.gcodeFiles?.length > 0 ? (
                                <div className="overflow-x-auto">
                                  <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm bg-white dark:bg-gray-800 rounded-lg overflow-hidden">
                                    <thead className="bg-gray-100 dark:bg-gray-700">
                                      <tr>
                                        {["Filename", "Status", "Started At", "Time Remaining", "Duration", "Actions"].map((h) => (
                                          <th key={h} className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border-b border-gray-200 dark:border-gray-600">
                                            {h}
                                          </th>
                                        ))}
                                      </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                                      {job.gcodeFiles.map((file) => (
                                        <tr key={file.id} className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                                          <td className="px-3 py-2 text-gray-800 dark:text-gray-200">{file.filename}</td>
                                          <td className="px-3 py-2">
                                            <span className={`px-2 py-1 rounded text-xs font-medium ${fileStatusClass(file.status)}`}>
                                              {file.status}
                                            </span>
                                          </td>
                                          <td className="px-3 py-2 text-gray-600 dark:text-gray-400">
                                            <FormatDate iso={file.startedAt} />
                                          </td>
                                          <td className="px-3 py-2 text-gray-700 dark:text-gray-300">
                                            {file.remainingTimeSeconds != null
                                              ? formatDuration(Math.max(file.remainingTimeSeconds, 0))
                                              : file.startedAt ? (() => {
                                                  const elapsed = Math.floor((Date.now() - new Date(file.startedAt).getTime()) / 1000)
                                                  return formatDuration(Math.max(file.durationSeconds - elapsed, 0))
                                                })() : "-"}
                                          </td>
                                          <td className="px-3 py-2 text-gray-700 dark:text-gray-300">
                                            {formatDuration(file.durationSeconds)}
                                          </td>
                                          <td className="px-3 py-2">
                                            <div className="flex gap-2 flex-wrap">
                                              <a
                                                href={`${API_URL}${file.downloadUrl}`}
                                                download
                                                className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded-md text-sm transition"
                                              >
                                                Download
                                              </a>
                                              <button
                                                onClick={(e) => { e.stopPropagation(); handleDownloadStls(file.id) }}
                                                className="bg-indigo-600 hover:bg-indigo-700 text-white px-3 py-1 rounded-md text-sm transition"
                                              >
                                                ⬇ STLs
                                              </button>
                                              <button
                                                onClick={(e) => { e.stopPropagation(); handleRequeue(file.id, file.filename) }}
                                                className="bg-orange-600 hover:bg-orange-700 text-white px-3 py-1 rounded-md text-sm transition"
                                              >
                                                🔄 Requeue
                                              </button>
                                              <button
                                                onClick={(e) => { e.stopPropagation(); handleDeleteGcodeFile(file.id, file.filename) }}
                                                className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded-md text-sm transition"
                                              >
                                                🗑️ Delete
                                              </button>
                                            </div>
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              ) : (
                                <p className="text-gray-500 dark:text-gray-400 text-sm">
                                  No G-code files linked to this job.
                                </p>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Rename Modal */}
      {renameModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 w-full max-w-md">
            <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">Rename Job</h2>
            <input
              type="text"
              value={renameInput}
              onChange={(e) => setRenameInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleRename(); if (e.key === "Escape") setRenameModal(null) }}
              autoFocus
              className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-4 py-2 text-sm text-gray-800 dark:text-gray-200 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-400 mb-4"
            />
            <p className="text-xs text-gray-500 dark:text-gray-400 mb-4">
              This will also rename all linked G-code files.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setRenameModal(null)}
                className="px-4 py-2 rounded-lg text-sm bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-200 hover:bg-gray-300 dark:hover:bg-gray-500 transition"
              >
                Cancel
              </button>
              <button
                onClick={handleRename}
                disabled={!renameInput.trim()}
                className="px-4 py-2 rounded-lg text-sm bg-blue-600 hover:bg-blue-700 text-white transition disabled:opacity-50"
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}