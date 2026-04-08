"use client"

import { useState, useRef, useEffect } from "react"
import * as THREE from "three"
import { STLLoader } from "three/examples/jsm/loaders/STLLoader.js"
import { authenticatedFetch } from '@/lib/auth'

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type FileWithParams = {
  file: File
  instances: number
  material: string
  color: string
  infill: string
  brim: string
  support: string
}

declare module 'react' {
  interface InputHTMLAttributes<T> extends HTMLAttributes<T> {
    webkitdirectory?: string
    directory?: string
  }
}

const infillOptions = ["5","10","15","20","30","40","50","60","70","80","90","100"]
const yesNo = ["yes", "no"]
const supportOptions = ["snug", "organic", "off"]

const selectClass = "border border-gray-300 dark:border-gray-600 rounded-md px-2 py-1 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 text-sm"
const inputClass = "border border-gray-300 dark:border-gray-600 rounded-md px-2 py-1 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
const thClass = "px-3 py-2 border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 font-semibold bg-gray-100 dark:bg-gray-700"
const tdClass = "px-3 py-2 border border-gray-200 dark:border-gray-700 text-gray-800 dark:text-gray-200"

/* ----------------------------------------
   STL Preview Component
---------------------------------------- */
function STLPreview({ file }: { file: File }) {
  const mountRef = useRef<HTMLDivElement>(null)
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!mountRef.current) return
    const container = mountRef.current
    const width = 300, height = 300

    const scene = new THREE.Scene()
    scene.background = new THREE.Color(0x1f2937) // dark bg for preview

    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 10000)
    camera.position.set(100, 100, 80)

    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setSize(width, height)
    rendererRef.current = renderer
    container.appendChild(renderer.domElement)

    scene.add(new THREE.AmbientLight(0xffffff, 0.6))
    const dl1 = new THREE.DirectionalLight(0xffffff, 0.8)
    dl1.position.set(1, 1, 1)
    scene.add(dl1)
    const dl2 = new THREE.DirectionalLight(0xffffff, 0.4)
    dl2.position.set(-1, -1, -1)
    scene.add(dl2)

    let animationId: number | null = null
    let mesh: THREE.Mesh | null = null

    const loader = new STLLoader()
    const reader = new FileReader()

    reader.onload = (e) => {
      try {
        const arrayBuffer = e.target?.result as ArrayBuffer
        if (!arrayBuffer) throw new Error("Failed to read file")

        const geometry = loader.parse(arrayBuffer)
        geometry.rotateX(-Math.PI / 2)
        geometry.computeBoundingBox()
        const boundingBox = geometry.boundingBox!
        const center = new THREE.Vector3()
        boundingBox.getCenter(center)
        geometry.translate(-center.x, -center.y, -center.z)

        const size = new THREE.Vector3()
        boundingBox.getSize(size)
        const maxDim = Math.max(size.x, size.y, size.z)
        geometry.scale(80 / maxDim, 80 / maxDim, 80 / maxDim)

        mesh = new THREE.Mesh(geometry, new THREE.MeshPhongMaterial({
          color: 0x4a90e2, specular: 0x111111, shininess: 200
        }))
        scene.add(mesh)
        camera.position.set(100, 100, 80)
        camera.lookAt(0, 0, 0)
        setLoading(false)

        const animate = () => {
          animationId = requestAnimationFrame(animate)
          if (mesh) mesh.rotation.y += 0.01
          if (rendererRef.current) rendererRef.current.render(scene, camera)
        }
        animate()
      } catch (err) {
        setError("Failed to load preview")
        setLoading(false)
      }
    }

    reader.onerror = () => { setError("Failed to read file"); setLoading(false) }
    reader.readAsArrayBuffer(file)

    return () => {
      if (animationId !== null) cancelAnimationFrame(animationId)
      if (mesh) {
        mesh.geometry?.dispose()
        if (Array.isArray(mesh.material)) mesh.material.forEach(m => m.dispose())
        else mesh.material?.dispose()
      }
      if (rendererRef.current) {
        rendererRef.current.dispose()
        if (rendererRef.current.domElement.parentNode === container)
          container.removeChild(rendererRef.current.domElement)
        rendererRef.current = null
      }
    }
  }, [file])

  return (
    <div className="bg-gray-800 border-2 border-gray-600 rounded-lg shadow-xl p-2">
      <div ref={mountRef} className="relative w-[300px] h-[300px]">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-700 rounded">
            <div className="text-sm text-gray-300">Loading preview...</div>
          </div>
        )}
        {error && (
          <div className="absolute inset-0 flex items-center justify-center bg-red-900/50 rounded">
            <div className="text-sm text-red-300">{error}</div>
          </div>
        )}
      </div>
      <div className="text-xs text-gray-400 mt-2 text-center truncate max-w-[300px]">
        {file.name}
      </div>
    </div>
  )
}

/* ----------------------------------------
   PAGE COMPONENT
---------------------------------------- */
export default function NewJobPage() {
  const [jobName, setJobName] = useState("")
  const [files, setFiles] = useState<FileWithParams[]>([])
  const [hoveredFile, setHoveredFile] = useState<File | null>(null)
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 })
  const [materialColors, setMaterialColors] = useState<Record<string, string[]>>({})
  const [loadingMaterials, setLoadingMaterials] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [failedFiles, setFailedFiles] = useState<string[]>([])

  const fileInputRef = useRef<HTMLInputElement>(null)
  const folderInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    async function load() {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/material-colors`)
        const data = await res.json()
        setMaterialColors(data)
      } catch (e) {
        console.error("Failed to load materials/colors:", e)
        alert("⚠️ Failed to load materials/colors from backend.")
      } finally {
        setLoadingMaterials(false)
      }
    }
    load()
  }, [])

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => setMousePosition({ x: e.clientX, y: e.clientY })
    window.addEventListener("mousemove", handleMouseMove)
    return () => window.removeEventListener("mousemove", handleMouseMove)
  }, [])

  const isSTL = (file: File) => file.name.toLowerCase().endsWith(".stl")
  const isGcode = (file: File) => file.name.toLowerCase().endsWith(".gcode")
  const isAllowedFile = (file: File) => isSTL(file) || isGcode(file)
  const stripExtension = (name: string) => name.replace(/\.(stl|gcode)$/i, "")

  const clearAll = () => {
    setFiles([])
    setJobName("")
    setHoveredFile(null)
    if (fileInputRef.current) fileInputRef.current.value = ""
    if (folderInputRef.current) folderInputRef.current.value = ""
  }

  const handleFilesUpload = (fileList: FileList | null, fromFolder: boolean) => {
    if (!fileList) return
    const validFiles = Array.from(fileList).filter(isAllowedFile)
    if (validFiles.length === 0) { alert("No valid .stl or .gcode files found."); return }

    const firstMaterial = Object.keys(materialColors)[0] || "PLA"
    const firstColor = materialColors[firstMaterial]?.[0] || "Black"

    setFiles(validFiles.map((file) => ({
      file, instances: 1, material: firstMaterial, color: firstColor,
      infill: "20", brim: "no", support: "on",
    })))

    if (fromFolder) {
      const relativePath = (validFiles[0] as any).webkitRelativePath as string | undefined
      setJobName(relativePath ? relativePath.split("/")[0] : "New Job")
    } else if (validFiles.length === 1) {
      setJobName(stripExtension(validFiles[0].name))
    } else {
      setJobName("New Job")
    }
  }

  const updateFileParam = <K extends keyof FileWithParams>(index: number, key: K, value: FileWithParams[K]) => {
    setFiles((prev) => {
      const copy = [...prev]
      copy[index][key] = value
      if (key === "material") copy[index].color = materialColors[value as string]?.[0] || ""
      return copy
    })
  }

  const applyToAll = <K extends keyof FileWithParams>(key: K, value: FileWithParams[K]) => {
    setFiles((prev) => prev.map((f) => {
      if ((key === "material" || key === "infill" || key === "brim" || key === "support") && !isSTL(f.file)) return f
      const updated = { ...f, [key]: value }
      if (key === "material") updated.color = materialColors[value as string]?.[0] || f.color
      return updated
    }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setFailedFiles([])

    const formData = new FormData()
    formData.append("jobName", jobName)

    const user = JSON.parse(sessionStorage.getItem("user") || "{}")
    if (!user.userId) { alert("No logged-in user found!"); setSubmitting(false); return }
    formData.append("userId", user.userId.toString())
    files.forEach((f) => formData.append("files", f.file))
    formData.append("metadata", JSON.stringify(files.map((f) => ({
      filename: f.file.name, material: f.material, color: f.color,
      infill: f.infill, brim: f.brim, support: f.support, instances: f.instances,
    }))))

    try {
      const res = await fetch(`${API_URL}/api/jobs/create`, {
        method: "POST", credentials: 'include', body: formData,
      })
      if (!res.ok) throw new Error(await res.text())
      const data = await res.json()

      const failed: string[] = data.failedFiles ?? []
      clearAll()
      if (failed.length > 0) {
        setFailedFiles(failed)
      } else {
        alert(`Job "${data.name}" created successfully!`)
      }
    } catch (err) {
      console.error("Job creation failed:", err)
      alert("Failed to create job. Check backend logs.")
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingMaterials) {
    return (
      <div className="p-6 w-full h-full bg-gray-50 dark:bg-gray-900 transition-colors">
        <p className="text-gray-600 dark:text-gray-400">Loading materials...</p>
      </div>
    )
  }

  const allColors = Array.from(new Set(Object.values(materialColors).flat()))

  return (
    <div className="p-6 w-full h-full bg-gray-50 dark:bg-gray-900 flex flex-col gap-6 transition-colors duration-200">

      <form onSubmit={handleSubmit} className="bg-white dark:bg-gray-800 p-6 rounded-xl shadow-md flex flex-col gap-4 transition-colors duration-200">

        {/* Job Name */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Job Name
          </label>
          <input
            type="text"
            value={jobName}
            onChange={(e) => setJobName(e.target.value)}
            required
            className="w-full border border-gray-300 dark:border-gray-600 rounded-md px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
        </div>

        {/* File Buttons */}
        <div className="flex gap-2 flex-wrap">
          <button type="button"
            onClick={() => { clearAll(); fileInputRef.current?.click() }}
            className="bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 px-4 py-2 rounded-md hover:bg-gray-300 dark:hover:bg-gray-600 transition"
          >
            Select File
          </button>
          <button type="button"
            onClick={() => { clearAll(); folderInputRef.current?.click() }}
            className="bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 px-4 py-2 rounded-md hover:bg-gray-300 dark:hover:bg-gray-600 transition"
          >
            Select Folder
          </button>
          {files.length > 0 && (
            <button type="button" onClick={clearAll}
              className="bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 px-4 py-2 rounded-md hover:bg-red-200 dark:hover:bg-red-900/50 transition"
            >
              Clear Files
            </button>
          )}
        </div>

        <input type="file" accept=".stl,.gcode" multiple ref={fileInputRef} className="hidden"
          onChange={(e) => handleFilesUpload(e.target.files, false)} />
        <input type="file" webkitdirectory="" ref={folderInputRef} className="hidden"
          onChange={(e) => handleFilesUpload(e.target.files, true)} />

        {/* File Table */}
        {files.length > 0 && (
          <div className="overflow-x-auto mt-4">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 rounded-lg">
              <thead>
                <tr>
                  {["File Name", "Instances", "Color", "Material", "Infill %", "Brim", "Support"].map(h => (
                    <th key={h} className={thClass}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {files.map((f, index) => {
                  const showFull = isSTL(f.file)
                  const availableMaterials = Object.keys(materialColors)
                  const availableColors = showFull ? materialColors[f.material] || [] : allColors

                  return (
                    <tr key={index} className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                      <td
                        className={`${tdClass} cursor-pointer hover:text-blue-600 dark:hover:text-blue-400`}
                        onMouseEnter={() => isSTL(f.file) && setHoveredFile(f.file)}
                        onMouseLeave={() => setHoveredFile(null)}
                      >
                        {(f.file as any).webkitRelativePath || f.file.name}
                        {isSTL(f.file) && (
                          <span className="ml-2 text-xs text-gray-400 dark:text-gray-500">(hover to preview)</span>
                        )}
                      </td>

                      <td className={tdClass}>
                        <input type="number" min={1} value={f.instances}
                          onChange={(e) => updateFileParam(index, "instances", +e.target.value)}
                          className={`${inputClass} w-16`}
                        />
                      </td>

                      <td className={tdClass}>
                        <select value={f.color}
                          onChange={(e) => updateFileParam(index, "color", e.target.value)}
                          className={selectClass}
                        >
                          {availableColors.map((c) => <option key={c} value={c}>{c}</option>)}
                        </select>
                      </td>

                      {showFull ? (
                        <>
                          <td className={tdClass}>
                            <select value={f.material}
                              onChange={(e) => updateFileParam(index, "material", e.target.value)}
                              className={selectClass}
                            >
                              {availableMaterials.map((m) => <option key={m} value={m}>{m}</option>)}
                            </select>
                          </td>
                          <td className={tdClass}>
                            <select value={f.infill}
                              onChange={(e) => updateFileParam(index, "infill", e.target.value)}
                              className={selectClass}
                            >
                              {infillOptions.map((i) => <option key={i} value={i}>{i}</option>)}
                            </select>
                          </td>
                          <td className={tdClass}>
                            <select value={f.brim}
                              onChange={(e) => updateFileParam(index, "brim", e.target.value)}
                              className={selectClass}
                            >
                              {yesNo.map((v) => <option key={v} value={v}>{v}</option>)}
                            </select>
                          </td>
                          <td className={tdClass}>
                            <select value={f.support}
                              onChange={(e) => updateFileParam(index, "support", e.target.value)}
                              className={selectClass}
                            >
                              {supportOptions.map((v) => <option key={v} value={v}>{v}</option>)}
                            </select>
                          </td>
                        </>
                      ) : (
                        <td colSpan={4} className={`${tdClass} text-gray-400 dark:text-gray-500 text-center italic`}>
                          Not applicable for .gcode
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
              <tfoot>
                <tr className="bg-amber-50 dark:bg-amber-900/20 border-t-2 border-amber-300 dark:border-amber-600">
                  <td className={`${tdClass} font-semibold text-amber-700 dark:text-amber-400 whitespace-nowrap`}>
                    Apply to all
                  </td>
                  <td className={tdClass}>
                    <input type="number" min={1} placeholder="—"
                      onChange={(e) => e.target.value && applyToAll("instances", +e.target.value)}
                      className={`${inputClass} w-16`}
                    />
                  </td>
                  <td className={tdClass}>
                    <select defaultValue="" onChange={(e) => e.target.value && applyToAll("color", e.target.value)} className={selectClass}>
                      <option value="" disabled>—</option>
                      {allColors.map((c) => <option key={c} value={c}>{c}</option>)}
                    </select>
                  </td>
                  <td className={tdClass}>
                    <select defaultValue="" onChange={(e) => e.target.value && applyToAll("material", e.target.value)} className={selectClass}>
                      <option value="" disabled>—</option>
                      {Object.keys(materialColors).map((m) => <option key={m} value={m}>{m}</option>)}
                    </select>
                  </td>
                  <td className={tdClass}>
                    <select defaultValue="" onChange={(e) => e.target.value && applyToAll("infill", e.target.value)} className={selectClass}>
                      <option value="" disabled>—</option>
                      {infillOptions.map((i) => <option key={i} value={i}>{i}</option>)}
                    </select>
                  </td>
                  <td className={tdClass}>
                    <select defaultValue="" onChange={(e) => e.target.value && applyToAll("brim", e.target.value)} className={selectClass}>
                      <option value="" disabled>—</option>
                      {yesNo.map((v) => <option key={v} value={v}>{v}</option>)}
                    </select>
                  </td>
                  <td className={tdClass}>
                    <select defaultValue="" onChange={(e) => e.target.value && applyToAll("support", e.target.value)} className={selectClass}>
                      <option value="" disabled>—</option>
                      {supportOptions.map((v) => <option key={v} value={v}>{v}</option>)}
                    </select>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="mt-4 bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 transition w-fit disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
        >
          {submitting && (
            <svg className="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
            </svg>
          )}
          {submitting ? "Slicing & Scheduling…" : "Submit Job"}
        </button>
      </form>

      {/* Failed files warning */}
      {failedFiles.length > 0 && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-300 dark:border-red-700 rounded-xl p-4 flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <h2 className="text-red-700 dark:text-red-400 font-semibold">
              ⚠️ {failedFiles.length} file{failedFiles.length > 1 ? "s" : ""} could not be sliced
            </h2>
            <button
              onClick={() => setFailedFiles([])}
              className="text-red-500 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 text-sm underline"
            >
              Dismiss
            </button>
          </div>
          <p className="text-sm text-red-600 dark:text-red-400">
            The rest of the job was created normally. These files were skipped:
          </p>
          <ul className="list-disc list-inside text-sm text-red-700 dark:text-red-300 space-y-0.5">
            {failedFiles.map((name, i) => (
              <li key={i}>{name}</li>
            ))}
          </ul>
        </div>
      )}

      {/* STL Preview Tooltip */}
      {hoveredFile && (
        <div className="fixed z-50 pointer-events-none"
          style={{ left: mousePosition.x + 20, top: mousePosition.y - 150 }}
        >
          <STLPreview file={hoveredFile} />
        </div>
      )}
    </div>
  )
}