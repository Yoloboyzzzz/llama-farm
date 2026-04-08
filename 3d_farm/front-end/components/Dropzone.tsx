"use client"

import { useState, useCallback } from "react"
import { cn } from "@/lib/utils"

export default function Dropzone({
  onFilesSelected,
  className,
}: {
  onFilesSelected: (files: FileList) => void
  className?: string
}) {
  const [isDragging, setIsDragging] = useState(false)
  const [fileNames, setFileNames] = useState<string[]>([])
  const [error, setError] = useState<string>("")

  const filterStlFiles = (files: FileList): File[] => {
    const stlFiles = Array.from(files).filter(f => 
      f.name.toLowerCase().endsWith('.stl')
    )
    
    if (stlFiles.length === 0) {
      setError("Only .stl files are accepted")
      setTimeout(() => setError(""), 3000)
      return []
    }
    
    if (stlFiles.length < files.length) {
      setError(`${files.length - stlFiles.length} non-STL file(s) ignored`)
      setTimeout(() => setError(""), 3000)
    } else {
      setError("")
    }
    
    return stlFiles
  }

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault()
      setIsDragging(false)
      const files = e.dataTransfer.files
      if (files && files.length > 0) {
        const stlFiles = filterStlFiles(files)
        if (stlFiles.length > 0) {
          setFileNames(stlFiles.map(f => f.name))
          // Convert array back to FileList-like structure
          const dt = new DataTransfer()
          stlFiles.forEach(f => dt.items.add(f))
          onFilesSelected(dt.files)
        }
      }
    },
    [onFilesSelected]
  )

  const handleBrowse = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (files && files.length > 0) {
      const stlFiles = filterStlFiles(files)
      if (stlFiles.length > 0) {
        setFileNames(stlFiles.map(f => f.name))
        const dt = new DataTransfer()
        stlFiles.forEach(f => dt.items.add(f))
        onFilesSelected(dt.files)
      }
    }
  }

  const hasFiles = fileNames.length > 0

  return (
    <div
      className={cn(
        "flex flex-col rounded-xl border-2 border-dashed p-6 text-center shadow-sm transition-colors duration-200",
        hasFiles ? "items-start" : "items-center justify-center min-h-[10rem]",
        "border-gray-400 dark:border-gray-600 bg-white dark:bg-gray-800",
        isDragging && "border-blue-500 bg-blue-50 dark:bg-blue-900/20 dark:border-blue-400",
        !hasFiles && className
      )}
      onDragOver={(e) => { e.preventDefault(); setIsDragging(true) }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
    >
      <input
        type="file"
        multiple
        accept=".stl"
        onChange={handleBrowse}
        className="hidden"
        id="fileInput"
      />

      {/* Upload prompt - always visible */}
      <div className={cn("flex flex-col items-center w-full", hasFiles && "mb-4")}>
        <span className="text-3xl mb-2">📂</span>
        <label
          htmlFor="fileInput"
          className="cursor-pointer text-gray-600 dark:text-gray-300 hover:text-blue-600 dark:hover:text-blue-400 hover:underline transition-colors"
        >
          Drag & drop STL files here, or{" "}
          <span className="text-blue-600 dark:text-blue-400 font-medium">click to select</span>
        </label>
        <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
          Only .stl files accepted
        </p>
      </div>

      {/* Error message */}
      {error && (
        <div className="w-full bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 rounded px-3 py-2 mb-3">
          <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
        </div>
      )}

      {/* File list - grows naturally, no fixed height cap */}
      {hasFiles && (
        <div className="w-full text-sm text-gray-700 dark:text-gray-300 border-t border-gray-200 dark:border-gray-700 pt-3">
          <p className="font-semibold mb-2 text-left">
            Selected {fileNames.length} file{fileNames.length > 1 ? "s" : ""}:
          </p>
          <ul className="text-left space-y-1">
            {fileNames.map((name, idx) => (
              <li
                key={idx}
                className="text-gray-600 dark:text-gray-400 truncate flex items-center gap-1.5"
              >
                <span className="text-green-500 text-xs">✓</span>
                {name}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}