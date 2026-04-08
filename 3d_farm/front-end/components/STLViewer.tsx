"use client"

import { useEffect, useRef, useCallback } from "react"
import * as THREE from "three"
import { STLLoader } from "three/examples/jsm/loaders/STLLoader.js"
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js"
import { computeFaceGroups, MeshInfo } from "@/lib/stlProcessor"

function groupColor(index: number, total: number): THREE.Color {
  const color = new THREE.Color()
  color.setHSL(index / Math.max(total, 1), 0.5, 0.55)
  return color
}

const HIGHLIGHT = new THREE.Color(0xff6b35)

interface SceneRef {
  renderer: THREE.WebGLRenderer
  scene: THREE.Scene
  camera: THREE.PerspectiveCamera
  controls: OrbitControls
  mesh: THREE.Mesh
  geometry: THREE.BufferGeometry
  colorArr: Float32Array
  faceGroups: Int32Array
  numGroups: number
  baseColors: THREE.Color[]
  cleanup: () => void
}

interface STLViewerProps {
  file: File
  selectedGroup: number | null
  onGroupClick: (groupIndex: number) => void
  onMeshProcessed: (info: MeshInfo) => void
}

export default function STLViewer({ file, selectedGroup, onGroupClick, onMeshProcessed }: STLViewerProps) {
  const mountRef = useRef<HTMLDivElement>(null)
  const sceneRef = useRef<SceneRef | null>(null)

  useEffect(() => {
    if (!file) return
    const mount = mountRef.current
    if (!mount) return

    const loader = new STLLoader()
    let cancelled = false

    file.arrayBuffer().then((buffer) => {
      if (cancelled) return

      const geometry = loader.parse(buffer)
      geometry.computeVertexNormals()

      const positions = geometry.attributes.position.array as Float32Array
      const meshInfo = computeFaceGroups(positions)
      const { faceGroups, numGroups } = meshInfo
      onMeshProcessed(meshInfo)

      const renderer = new THREE.WebGLRenderer({ antialias: true })
      renderer.setPixelRatio(window.devicePixelRatio)
      renderer.setSize(mount.clientWidth, mount.clientHeight)
      renderer.setClearColor(0x1a1a2e)
      mount.appendChild(renderer.domElement)

      const scene = new THREE.Scene()
      const camera = new THREE.PerspectiveCamera(45, mount.clientWidth / mount.clientHeight, 0.001, 100000)

      scene.add(new THREE.AmbientLight(0xffffff, 0.5))
      const dir1 = new THREE.DirectionalLight(0xffffff, 0.8)
      dir1.position.set(1, 2, 3)
      scene.add(dir1)
      const dir2 = new THREE.DirectionalLight(0xffffff, 0.3)
      dir2.position.set(-1, -1, -2)
      scene.add(dir2)

      const numTris = positions.length / 9
      const baseColors = Array.from({ length: numGroups }, (_, i) => groupColor(i, numGroups))

      const colorArr = new Float32Array(numTris * 9)
      for (let i = 0; i < numTris; i++) {
        const col = baseColors[faceGroups[i]] ?? new THREE.Color(0x888888)
        for (let j = 0; j < 3; j++) {
          colorArr[i * 9 + j * 3 + 0] = col.r
          colorArr[i * 9 + j * 3 + 1] = col.g
          colorArr[i * 9 + j * 3 + 2] = col.b
        }
      }
      geometry.setAttribute("color", new THREE.BufferAttribute(colorArr, 3))

      const material = new THREE.MeshPhongMaterial({ vertexColors: true, side: THREE.DoubleSide })
      const mesh = new THREE.Mesh(geometry, material)

      const edgeGeo = new THREE.WireframeGeometry(geometry)
      const edgeMat = new THREE.LineBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.12 })
      mesh.add(new THREE.LineSegments(edgeGeo, edgeMat))
      scene.add(mesh)

      geometry.computeBoundingBox()
      const box = geometry.boundingBox!
      const centre = new THREE.Vector3()
      box.getCenter(centre)
      const size = new THREE.Vector3()
      box.getSize(size)
      const maxDim = Math.max(size.x, size.y, size.z)
      mesh.position.sub(centre)

      camera.position.set(0, 0, maxDim * 2)
      camera.near = maxDim * 0.001
      camera.far  = maxDim * 100
      camera.updateProjectionMatrix()

      const controls = new OrbitControls(camera, renderer.domElement)
      controls.enableDamping = true

      sceneRef.current = {
        renderer, scene, camera, controls, mesh, geometry,
        colorArr, faceGroups, numGroups, baseColors,
        cleanup: () => {},
      }

      let animId: number
      function animate() {
        animId = requestAnimationFrame(animate)
        controls.update()
        renderer.render(scene, camera)
      }
      animate()

      const observer = new ResizeObserver(() => {
        if (!mount) return
        renderer.setSize(mount.clientWidth, mount.clientHeight)
        camera.aspect = mount.clientWidth / mount.clientHeight
        camera.updateProjectionMatrix()
      })
      observer.observe(mount)

      sceneRef.current.cleanup = () => {
        cancelAnimationFrame(animId)
        observer.disconnect()
        controls.dispose()
        geometry.dispose()
        material.dispose()
        edgeGeo.dispose()
        edgeMat.dispose()
        renderer.dispose()
        if (mount.contains(renderer.domElement)) mount.removeChild(renderer.domElement)
      }
    })

    return () => {
      cancelled = true
      sceneRef.current?.cleanup?.()
      sceneRef.current = null
    }
  }, [file]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const s = sceneRef.current
    if (!s) return
    const { geometry, colorArr, faceGroups, numGroups, baseColors } = s
    const numTris = faceGroups.length
    for (let i = 0; i < numTris; i++) {
      const col = faceGroups[i] === selectedGroup
        ? HIGHLIGHT
        : (baseColors[faceGroups[i]] ?? new THREE.Color(0x888888))
      for (let j = 0; j < 3; j++) {
        colorArr[i * 9 + j * 3 + 0] = col.r
        colorArr[i * 9 + j * 3 + 1] = col.g
        colorArr[i * 9 + j * 3 + 2] = col.b
      }
    }
    geometry.attributes.color.needsUpdate = true
  }, [selectedGroup])

  const pointerDown = useRef<{ x: number; y: number } | null>(null)

  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    pointerDown.current = { x: e.clientX, y: e.clientY }
  }, [])

  const handlePointerUp = useCallback((e: React.PointerEvent) => {
    const start = pointerDown.current
    if (!start) return
    const dx = e.clientX - start.x, dy = e.clientY - start.y
    if (dx * dx + dy * dy > 16) return

    const s = sceneRef.current
    if (!s) return
    const { renderer, camera, mesh, faceGroups } = s
    const rect = renderer.domElement.getBoundingClientRect()

    const ndc = new THREE.Vector2(
      ((e.clientX - rect.left) / rect.width)  *  2 - 1,
      ((e.clientY - rect.top)  / rect.height) * -2 + 1,
    )

    const raycaster = new THREE.Raycaster()
    raycaster.setFromCamera(ndc, camera)
    const hits = raycaster.intersectObject(mesh, false)
    if (hits.length === 0) return

    onGroupClick(faceGroups[hits[0].faceIndex!])
  }, [onGroupClick])

  return (
    <div
      ref={mountRef}
      className="w-full h-full"
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
    />
  )
}
