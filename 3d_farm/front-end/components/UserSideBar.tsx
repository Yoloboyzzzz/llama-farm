"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import Image from "next/image"

const navItems = [
  { href: "/user/dashboard", label: "Dashboard", icon: "📊" },
  { href: "/user/estimate",  label: "Estimate",  icon: "📐" },
  { href: "/user/stl-svg",  label: "Stl to SVG",  icon: "📐" },
]

type UserSidebarProps = {
  isOpen: boolean
  onClose: () => void
}

export default function UserSidebar({ isOpen, onClose }: UserSidebarProps) {
  const pathname = usePathname()

  return (
    <aside className={`
      fixed inset-y-0 left-0 z-40 w-52 h-full
      bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700
      flex flex-col transition-all duration-200
      md:relative md:translate-x-0 md:z-auto
      ${isOpen ? "translate-x-0" : "-translate-x-full"}
    `}>
      {/* Logo */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-700 relative flex items-center justify-center">
        <Image
          src="/FabLab_Logo.png"
          alt="FabLab Leuven"
          width={120}
          height={60}
          className="object-contain dark:invert"
        />
        <button
          onClick={onClose}
          className="md:hidden absolute right-4 p-1 rounded text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          aria-label="Close menu"
        >
          ✕
        </button>
      </div>

      {/* Nav */}
      <nav className="flex-1 p-3 space-y-1">
        {navItems.map(({ href, label, icon }) => {
          const active = pathname === href || pathname.startsWith(href + "/")
          return (
            <Link
              key={href}
              href={href}
              onClick={onClose}
              className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                active
                  ? "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300"
                  : "text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
              }`}
            >
              <span>{icon}</span>
              {label}
            </Link>
          )
        })}
      </nav>

      {/* Footer */}
      <div className="p-3 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-400 dark:text-gray-600 text-center">
        🦙 Llama Farm v1.0
      </div>
    </aside>
  )
}