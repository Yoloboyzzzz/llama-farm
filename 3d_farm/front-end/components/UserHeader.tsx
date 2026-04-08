"use client"

import ProfileDropdown from "@/components/ProfileDropdown"

type UserHeaderProps = {
  onMenuToggle: () => void
}

export default function UserHeader({ onMenuToggle }: UserHeaderProps) {
  return (
    <header className="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 px-6 py-4 flex justify-between items-center sticky top-0 z-10 transition-colors">
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuToggle}
          className="md:hidden p-2 rounded-md text-gray-500 hover:text-gray-700 hover:bg-gray-100 dark:text-gray-400 dark:hover:text-gray-200 dark:hover:bg-gray-800 transition-colors"
          aria-label="Toggle menu"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <span className="text-2xl">🦙</span>
        <h1 className="text-xl font-semibold text-gray-800 dark:text-gray-100">Dashboard</h1>
      </div>

      <ProfileDropdown />
    </header>
  )
}