"use client"

import { useState, useCallback } from "react"
import STLViewer from "@/components/STLViewer"
import { generateSVG, MeshInfo } from "@/lib/stlProcessor"

export default function StlSvgPage() {
  const [file, setFile]                   = useState<File | null>(null)
  const [meshInfo, setMeshInfo]           = useState<MeshInfo | null>(null)
  const [selectedGroup, setSelectedGroup] = useState<number | null>(null)
  const [status, setStatus]               = useState("Upload an STL file to begin.")
  const [loading, setLoading]             = useState(false)

  const handleFile = useCallback((incoming: File | null | undefined) => {
    if (!incoming) return
    setFile(incoming)
    setMeshInfo(null)
    setSelectedGroup(null)
    setLoading(true)
    setStatus(`Parsing ${incoming.name}…`)
  }, [])

  const onFileInput = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    handleFile(e.target.files?.[0])
  }, [handleFile])

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    handleFile(e.dataTransfer.files?.[0])
  }, [handleFile])

  const onMeshProcessed = useCallback((info: MeshInfo) => {
    setMeshInfo(info)
    setLoading(false)
    setStatus(
      `Loaded: ${info.faceGroups.length} triangles, ${info.numGroups} face groups. Click a face to select it.`
    )
  }, [])

  const onGroupClick = useCallback((groupIndex: number) => {
    setSelectedGroup(groupIndex)
    setStatus(`Face group ${groupIndex} selected. Click "Download SVG" to export.`)
  }, [])

  const downloadSVG = useCallback(() => {
    if (selectedGroup === null || !meshInfo) return
    const svg = generateSVG(selectedGroup, meshInfo)
    if (!svg) { setStatus("Could not generate SVG for this face."); return }
    const blob = new Blob([svg], { type: "image/svg+xml" })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement("a")
    a.href     = url
    a.download = `face_${selectedGroup}.svg`
    a.click()
    URL.revokeObjectURL(url)
    setStatus(`SVG for face group ${selectedGroup} downloaded.`)
  }, [selectedGroup, meshInfo])

  return (
    <div className="flex flex-col h-full min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <header className="flex flex-wrap items-center gap-3 px-4 py-3 bg-gray-900 border-b border-gray-800">
        <h1 className="text-lg font-semibold text-white mr-2">STL to SVG</h1>

        <label
          className={`cursor-pointer px-4 py-1.5 rounded text-sm font-medium transition-colors
            ${loading
              ? "bg-gray-700 text-gray-400 cursor-not-allowed"
              : "bg-blue-600 hover:bg-blue-500 text-white"}`}
          onDrop={onDrop}
          onDragOver={(e) => e.preventDefault()}
        >
          {loading ? "Processing…" : "Upload STL"}
          <input
            type="file"
            accept=".stl"
            onChange={onFileInput}
            className="hidden"
            disabled={loading}
          />
        </label>

        <button
          onClick={downloadSVG}
          disabled={selectedGroup === null || loading}
          className="px-4 py-1.5 rounded text-sm font-medium transition-colors
            disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed
            enabled:bg-emerald-600 enabled:hover:bg-emerald-500 enabled:text-white"
        >
          Download SVG
        </button>

        <span className="text-sm text-gray-400 ml-1">{status}</span>
      </header>

      {/* Viewer */}
      <div className="flex-1 relative">
        {file ? (
          <STLViewer
            file={file}
            selectedGroup={selectedGroup}
            onGroupClick={onGroupClick}
            onMeshProcessed={onMeshProcessed}
          />
        ) : (
          <div
            className="absolute inset-0 flex items-center justify-center border-2 border-dashed border-gray-700 m-4 rounded-lg cursor-pointer"
            onDrop={onDrop}
            onDragOver={(e) => e.preventDefault()}
            onClick={() => document.querySelector<HTMLInputElement>('input[type="file"]')?.click()}
          >
            <p className="text-gray-500 text-sm select-none">
              Drag &amp; drop an STL file here, or use the Upload button above.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
