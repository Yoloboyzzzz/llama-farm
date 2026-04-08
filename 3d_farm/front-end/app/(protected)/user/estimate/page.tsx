"use client"

import { useState, useEffect } from "react"
import Dropzone from "@/components/Dropzone"
import { authenticatedFetch } from '@/lib/auth'

export const API_URL = process.env.NEXT_PUBLIC_API_URL

type FileEntry = {
  file: File
  name: string
  instances: number
  material: string
  color: string
  infill: number
}

function formatTime(seconds: number) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${h}h ${m}m ${s}s`
}

const COST_PER_GRAM = 0.10

const thClass = "border border-gray-300 dark:border-gray-500 px-3 py-2 text-left font-bold text-gray-700 dark:text-white bg-gray-300 dark:bg-gray-950"
const tdClass = "border border-gray-300 dark:border-gray-600 px-3 py-2 text-gray-800 dark:text-gray-200"
const selectClass = "border border-gray-300 dark:border-gray-600 rounded px-2 py-1 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 text-sm"
const inputClass = "w-20 border border-gray-300 dark:border-gray-600 rounded px-2 py-1 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 text-sm"

function EstimateResultsTable({ results, files }: { results: any; files: FileEntry[] }) {

  const resultArray: any[] = Array.isArray(results)
    ? results
    : Array.isArray(results?.files)
    ? results.files
    : Array.isArray(results?.results)
    ? results.results
    : []

  if (resultArray.length === 0) {
    return (
      <div className="mt-10 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 p-6 shadow">
        <p className="text-gray-500 dark:text-gray-400 text-sm mb-2">
          No results — unexpected response shape. Check the console for the raw response.
        </p>
        <pre className="text-xs text-red-500 dark:text-red-400 overflow-auto bg-gray-50 dark:bg-gray-900 p-3 rounded">
          {JSON.stringify(results, null, 2)}
        </pre>
      </div>
    )
  }

  const rows = resultArray.map((res, i) => {
    const f = files[i]
    const pricePerPiece = res.grams * COST_PER_GRAM
    return {
      filename: res.filename,
      grams: res.grams,
      time: formatTime(res.seconds),
      instances: f?.instances ?? 1,
      pricePerPiece,
      lineTotal: pricePerPiece * (f?.instances ?? 1),
    }
  })

  const grandTotal = rows.reduce((acc, r) => acc + r.lineTotal, 0)

  return (
    <div className="mt-10 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 p-6 shadow transition-colors duration-200">
      <h2 className="text-xl font-bold mb-4 text-gray-800 dark:text-gray-100">Estimation Details</h2>
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            {["File", "Weight (g)", "Time", "Instances", "€/Piece", "Total €"].map(h => (
              <th key={h} className={thClass}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
              <td className={tdClass}>{r.filename}</td>
              <td className={tdClass}>{r.grams}</td>
              <td className={tdClass}>{r.time}</td>
              <td className={tdClass}>{r.instances}</td>
              <td className={tdClass}>€{r.pricePerPiece.toFixed(2)}</td>
              <td className={`${tdClass} font-semibold`}>€{r.lineTotal.toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="bg-gray-100 dark:bg-gray-950 font-bold text-base">
            <td className="border border-gray-300 dark:border-gray-600 px-3 py-3 text-gray-800 dark:text-gray-100" colSpan={5}>
              Total Price
            </td>
            <td className="border border-gray-300 dark:border-gray-600 px-3 py-3 text-gray-800 dark:text-gray-100">
              €{grandTotal.toFixed(2)}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

export default function EstimatePage() {
  const [files, setFiles] = useState<FileEntry[]>([])
  const [materialColors, setMaterialColors] = useState<Record<string, string[]>>({})
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<any>(null)

  useEffect(() => {
    async function load() {
      try {
        const res = await authenticatedFetch(`${API_URL}/api/material-colors`)
        const data = await res.json()
        setMaterialColors(data)
      } catch (e) {
        console.error("Failed to load materials:", e)
      }
    }
    load()
  }, [])

  function addFiles(list: FileList) {
    const defaultMaterial = Object.keys(materialColors)[0] || "PLA"
    const defaultColor = materialColors[defaultMaterial]?.[0] || "Black"
    setFiles(prev => [
      ...prev,
      ...Array.from(list).map(file => ({
        file, name: file.name, instances: 1,
        material: defaultMaterial, color: defaultColor, infill: 20,
      }))
    ])
  }

  function clearFiles() {
    setFiles([])
    setResult(null)
  }

  function updateField(index: number, field: keyof FileEntry, value: any) {
    setFiles(prev => {
      const copy = [...prev]
      if (field === "material") copy[index].color = materialColors[value]?.[0] || ""
      // @ts-ignore
      copy[index][field] = value
      return copy
    })
  }

  async function handleEstimate() {
    if (files.length === 0) return
    setLoading(true)
    try {
      const form = new FormData()
      files.forEach((f, i) => {
        form.append("files", f.file)
        form.append(`material_${i}`, f.material)
        form.append(`color_${i}`, f.color)
        form.append(`instances_${i}`, f.instances.toString())
        form.append(`infill_${i}`, f.infill.toString())
      })
      const res = await authenticatedFetch(`${API_URL}/api/estimate`, {
        method: "POST",
        body: form,
      })
      const data = await res.json()
      console.log("Estimate response:", data)
      setResult(data)
    } catch (err) {
      console.error("Estimate failed:", err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-8 max-w-5xl mx-auto bg-gray-50 dark:bg-gray-900 min-h-full transition-colors duration-200">
      <h1 className="text-2xl font-semibold mb-6 text-gray-800 dark:text-gray-100">Cost Estimation</h1>

      {/* ✅ Disclaimer Section */}
      <div className="mb-6 p-5 bg-amber-50 dark:bg-amber-900/20 border-l-4 border-amber-500 rounded-lg">
        <h2 className="text-lg font-bold text-amber-800 dark:text-amber-300 mb-3 flex items-center gap-2">
          ⚠️ Important Information
        </h2>
        <ul className="space-y-2 text-sm text-amber-900 dark:text-amber-200">
          <li className="flex items-start gap-2">
            <span className="text-amber-600 dark:text-amber-400 font-bold mt-0.5">•</span>
            <span>
              <strong>This is an estimation, not the final cost.</strong> The actual price is determined after printing by weighing the finished part.
            </span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600 dark:text-amber-400 font-bold mt-0.5">•</span>
            <span>
              <strong>Print orientation may be adjusted.</strong> Your file will be sliced in the orientation provided, but we reserve the right to reorient it for optimal print quality, strength, or efficiency.
            </span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600 dark:text-amber-400 font-bold mt-0.5">•</span>
            <span>
              <strong>Support material is included.</strong> All estimates are calculated with support structures enabled, which may increase material usage and print time.
            </span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600 dark:text-amber-400 font-bold mt-0.5">•</span>
            <span>
              <strong>Estimates only for FDM prints.</strong> The LLama Farm only supports FDM machines. For resin pieces we cannot provide estimates.
            </span>
          </li>
        </ul>
      </div>

      <Dropzone onFilesSelected={addFiles} className="mb-4" />

      <button
        onClick={clearFiles}
        className="mb-4 px-4 py-2 bg-red-400 hover:bg-red-500 dark:bg-red-700 dark:hover:bg-red-600 text-white rounded transition"
      >
        Clear Files
      </button>

      {files.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-gray-300 dark:border-gray-600 text-sm">
            <thead>
              <tr>
                {["File", "Instances", "Color", "Material", "Infill"].map(h => (
                  <th key={h} className={thClass}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {files.map((f, i) => (
                <tr key={i} className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                  <td className={tdClass}>{f.name}</td>
                  <td className={tdClass}>
                    <input type="number" min={1} value={f.instances}
                      onChange={e => updateField(i, "instances", Number(e.target.value))}
                      className={inputClass}
                    />
                  </td>
                  <td className={tdClass}>
                    <select value={f.color}
                      onChange={e => updateField(i, "color", e.target.value)}
                      className={selectClass}
                    >
                      {(materialColors[f.material] || []).map(c => (
                        <option key={c} value={c}>{c}</option>
                      ))}
                    </select>
                  </td>
                  <td className={tdClass}>
                    <select value={f.material}
                      onChange={e => updateField(i, "material", e.target.value)}
                      className={selectClass}
                    >
                      {Object.keys(materialColors).map(m => (
                        <option key={m} value={m}>{m}</option>
                      ))}
                    </select>
                  </td>
                  <td className={tdClass}>
                    <input type="number" min={0} max={100} value={f.infill}
                      onChange={e => updateField(i, "infill", Number(e.target.value))}
                      className={inputClass}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {files.length > 0 && (
        <button
          onClick={handleEstimate}
          disabled={loading}
          className="mt-6 px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded transition"
        >
          {loading ? "Estimating…" : "Estimate Cost"}
        </button>
      )}

      {result && <EstimateResultsTable results={result} files={files} />}
    </div>
  )
}