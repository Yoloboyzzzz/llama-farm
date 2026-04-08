"use client"

import { useState } from "react"
import Sidebar from "@/components/Sidebar"
import AdminHeader from "@/components/AdminHeader"
import AuthGuard from "@/components/AuthGuard"
import { Toaster } from "sonner"

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <AuthGuard requireAdmin>
      <div className="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
        <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        {/* Mobile backdrop */}
        {sidebarOpen && (
          <div
            className="fixed inset-0 z-30 bg-black/50 md:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}
        <div className="flex flex-col flex-1 overflow-hidden min-w-0">
          <AdminHeader onMenuToggle={() => setSidebarOpen((o) => !o)} />
          <main className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
            {children}
          </main>
        </div>
      </div>
      <Toaster richColors position="top-right" />
    </AuthGuard>
  )
}
