"use client"

import Link from "next/link"
import Image from "next/image"
import { usePathname } from "next/navigation"
import { useEffect, useState } from "react"
import { authenticatedFetch } from "@/lib/auth"

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

const navItems = [
  { name: "Dashboard", href: "/admin/dashboard", icon: "📊" },
  { name: "New Job",   href: "/admin/new_job",   icon: "➕" },
  { name: "Printers",  href: "/admin/printers",  icon: "🖨️" },
  { name: "Queue",     href: "/admin/queue",      icon: "📋" },
  { name: "Jobs",      href: "/admin/jobs",       icon: "💼" },
  { name: "Settings",  href: "/admin/settings",   icon: "⚙️" },
  { name: "Estimate",  href: "/admin/estimate",   icon: "📐" },
  { name: "STL to SVG",  href: "/admin/stl-svg",   icon: "📐" },
]

type SidebarProps = {
  isOpen: boolean
  onClose: () => void
}

export default function Sidebar({ isOpen, onClose }: SidebarProps) {
  const pathname = usePathname()
  const [queueCount, setQueueCount] = useState<number>(0)

  const fetchQueueCount = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/queue`)
      const data = await res.json()
      setQueueCount(data.length)
    } catch (err) {
      console.error("Failed to fetch queue count:", err)
    }
  }

  useEffect(() => {
    fetchQueueCount()
    const interval = setInterval(fetchQueueCount, 30000)
    return () => clearInterval(interval)
  }, [])

  return (
    <aside className={`
      fixed inset-y-0 left-0 z-40 w-64 h-full
      bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700
      flex flex-col transition-all duration-200
      md:relative md:translate-x-0 md:z-auto
      ${isOpen ? "translate-x-0" : "-translate-x-full"}
    `}>

      {/* Logo */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-700 relative flex items-center justify-center">
        <Link href="/admin/dashboard" onClick={onClose}>
          <Image
            src="/FabLab_Logo.png"
            alt="FabLab Logo"
            width={120}
            height={40}
            className="object-contain dark:invert"
          />
        </Link>
        <button
          onClick={onClose}
          className="md:hidden absolute right-4 p-1 rounded text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          aria-label="Close menu"
        >
          ✕
        </button>
      </div>

      {/* Nav */}
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const isQueue = item.name === "Queue"
          const hasItems = isQueue && queueCount > 0
          const displayName = hasItems ? `${item.name} (${queueCount})` : item.name

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onClose}
              className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors duration-150 ${
                hasItems ? "font-bold" : "font-medium"
              } ${
                pathname === item.href
                  ? "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300"
                  : "text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
              }`}
            >
              <span>{item.icon}</span>
              {displayName}
            </Link>
          )
        })}
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-gray-200 dark:border-gray-700">
        <p className="text-xs text-gray-400 dark:text-gray-500 text-center">
          🦙 Llama Farm v1.0
        </p>
      </div>
    </aside>
  )
}