"use client"

import { useState, useEffect } from "react"
import { usePathname } from "next/navigation"
import AdminProfileDropdown from "@/components/AdminProfileDropdown"
import { authenticatedFetch } from "@/lib/auth"

const navItems = [
  { name: "Dashboard",  href: "/admin/dashboard" },
  { name: "New Job",    href: "/admin/new_job" },
  { name: "Printers",   href: "/admin/printers" },
  { name: "Queue",      href: "/admin/queue" },
  { name: "Jobs",       href: "/admin/jobs" },
  { name: "Settings",   href: "/admin/settings" },
  { name: "Estimate",   href: "/admin/estimate" },
  { name: "STL to SVG", href: "/admin/stl-svg" },
]

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type NotificationData = {
  id: number
  type: string
  printerName: string
  printerIp: string
  message: string
  metadata: {
    color?: string
    filePath?: string
    jobId?: number
  }
  createdAt: string
}

type AdminHeaderProps = {
  onMenuToggle: () => void
}

export default function AdminHeader({ onMenuToggle }: AdminHeaderProps) {
  const pathname = usePathname()
  const pageTitle = navItems.find((item) => pathname.startsWith(item.href))?.name ?? "Dashboard"

  const [notifications, setNotifications] = useState<NotificationData[]>([])
  const [currentNotification, setCurrentNotification] = useState(0)
  const [showBubble, setShowBubble] = useState(false)
  const [notificationsEnabled, setNotificationsEnabled] = useState(true)
  const [loaded, setLoaded] = useState(false)

  // Fetch notifications from backend
  const fetchNotifications = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/notifications`)
      if (res.ok) {
        const data = await res.json()
        setNotifications(data)
      }
    } catch (err) {
      console.error("Failed to fetch notifications:", err)
    }
  }

  // Poll for new notifications every 10 seconds
  useEffect(() => {
    fetchNotifications()
    const interval = setInterval(fetchNotifications, 10000)
    return () => clearInterval(interval)
  }, [])

  // Cycle through notifications
  useEffect(() => {
    if (!notificationsEnabled || !loaded || notifications.length === 0) {
      setShowBubble(false)
      return
    }

    const initialTimer = setTimeout(() => {
      setShowBubble(true)
    }, 1000)

    const cycleTimer = setInterval(() => {
      setShowBubble(false)
      setTimeout(() => {
        setCurrentNotification((prev) => (prev + 1) % notifications.length)
        setShowBubble(true)
      }, 500)
    }, 8000)

    return () => {
      clearTimeout(initialTimer)
      clearInterval(cycleTimer)
    }
  }, [notificationsEnabled, loaded, notifications.length])

  const handleNotificationsLoaded = (enabled: boolean) => {
    setNotificationsEnabled(enabled)
    setLoaded(true)
  }

  // Handle notification action clicks
  const handleNotificationClick = async () => {
    const notification = notifications[currentNotification]
    if (!notification) return

    if (notification.type === "COLOR_CONFIRMATION") {
      const confirmed = window.confirm(
        `Confirm that ${notification.metadata.color || "the correct"} filament is loaded on ${notification.printerName}?`
      )
      
      if (confirmed) {
        try {
          const res = await authenticatedFetch(
            `${API_URL}/api/admin/notifications/${notification.id}/confirm-color`,
            { method: "POST" }
          )
          if (res.ok) {
            fetchNotifications()
          }
        } catch (err) {
          console.error("Failed to confirm color:", err)
        }
      }
    } else {
      try {
        const res = await authenticatedFetch(
          `${API_URL}/api/admin/notifications/${notification.id}/dismiss`,
          { method: "POST" }
        )
        if (res.ok) {
          fetchNotifications()
        }
      } catch (err) {
        console.error("Failed to dismiss notification:", err)
      }
    }
  }

  const currentNotif = notifications[currentNotification]

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
        <h1 className="text-xl font-semibold text-gray-800 dark:text-gray-100">{pageTitle}</h1>
      </div>

      <div className="relative">
        <AdminProfileDropdown 
          notificationsEnabled={notificationsEnabled}
          onToggleNotifications={setNotificationsEnabled}
          onNotificationsLoaded={handleNotificationsLoaded}
        />

        {showBubble && notificationsEnabled && currentNotif && (
          <div 
            className="absolute top-12 right-0 z-50 cursor-pointer hover:scale-105 transition-transform"
            onClick={handleNotificationClick}
          >
            <div className={`relative bg-white dark:bg-gray-800 rounded-2xl px-6 py-4 shadow-xl border-2 max-w-sm w-80 ${
              currentNotif.type === "PRINTER_ERROR"
                ? "border-red-500 dark:border-red-600"
                : "border-orange-400 dark:border-orange-500"
            }`}>
              <p className="text-base font-semibold text-gray-800 dark:text-gray-100 leading-snug">
                {currentNotif.type === "PRINTER_ERROR" && <span className="mr-1.5">🚨</span>}
                {currentNotif.type === "PRINTER_BUSY" && <span className="mr-1.5">🔄</span>}
                {currentNotif.message}
              </p>
              {currentNotif.type === "COLOR_CONFIRMATION" && (
                <button className="mt-3 w-full px-4 py-2 bg-green-500 text-white text-sm font-semibold rounded-lg hover:bg-green-600 transition">
                  ✅ Confirm & Start Print
                </button>
              )}
              <div className={`absolute -top-2 right-6 w-4 h-4 bg-white dark:bg-gray-800 border-l-2 border-t-2 transform rotate-45 ${
                currentNotif.type === "PRINTER_ERROR"
                  ? "border-red-500 dark:border-red-600"
                  : "border-orange-400 dark:border-orange-500"
              }`}></div>
            </div>
          </div>
        )}
      </div>
    </header>
  )
}