"use client"

import ProfileDropdown from "@/components/ProfileDropdown"

export default function Header() {
  return (
    <header className="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 px-6 py-4 flex justify-between items-center sticky top-0 z-10 transition-colors">
      <div className="flex items-center gap-3">
        <span className="text-2xl">🦙</span>
        <h1 className="text-xl font-semibold text-gray-800 dark:text-gray-100">Dashboard</h1>
      </div>

      <ProfileDropdown />
    </header>
  )
}