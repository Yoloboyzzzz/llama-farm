"use client"

import { useState, useRef, useEffect } from "react"
import { useRouter } from "next/navigation"
import { authenticatedFetch } from "@/lib/auth"

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type UserProfile = {
  userId: number
  name: string
  email: string
  role: string
  avatarEmoji: string
  avatarColor: string
  darkMode: boolean
  notificationsEnabled: boolean  // ✅ Added
}

const AVATAR_EMOJIS = [
  "🦙", "🐼", "🦊", "🐺", "🦁", "🐯", "🐻", "🐨",
  "🐸", "🐧", "🦋", "🐙", "🦄", "🐲", "🦖", "🤖",
  "👨‍💻", "👩‍💻", "🧑‍🔬", "🧑‍🎨", "🧑‍🚀", "🥷", "🧙", "🦸",
]

const AVATAR_COLORS = [
  "#f97316", "#3b82f6", "#10b981", "#8b5cf6", "#ef4444",
  "#f59e0b", "#06b6d4", "#ec4899", "#6b7280", "#1f2937",
]

type AdminProfileDropdownProps = {
  notificationsEnabled: boolean
  onToggleNotifications: (enabled: boolean) => void
  onNotificationsLoaded: (enabled: boolean) => void  // ✅ New callback
}

export default function AdminProfileDropdown({ 
  notificationsEnabled, 
  onToggleNotifications,
  onNotificationsLoaded 
}: AdminProfileDropdownProps) {
  const router = useRouter()
  const dropdownRef = useRef<HTMLDivElement>(null)

  const [open, setOpen] = useState(false)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [fetchError, setFetchError] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState("")
  const [saving, setSaving] = useState(false)
  const [showEmojiPicker, setShowEmojiPicker] = useState(false)

  useEffect(() => {
    fetchProfile()
  }, [])

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false)
        setEditing(false)
        setShowEmojiPicker(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [])

  useEffect(() => {
    if (profile?.darkMode) {
      document.documentElement.classList.add("dark")
    } else {
      document.documentElement.classList.remove("dark")
    }
  }, [profile?.darkMode])

  const fetchProfile = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/profile`)
      if (res.ok) {
        const data = await res.json()
        setProfile(data)
        setEditName(data.name)
        setFetchError(false)
        // ✅ Load saved notification preference
        onNotificationsLoaded(data.notificationsEnabled ?? true)
      } else {
        setFetchError(true)
      }
    } catch (err) {
      setFetchError(true)
    }
  }

  const saveProfile = async (updates: Partial<UserProfile>) => {
    if (!profile) return
    setSaving(true)
    const newProfile = { ...profile, ...updates }
    setProfile(newProfile)

    try {
      const res = await authenticatedFetch(`${API_URL}/api/profile`, {
        method: "PUT",
        body: JSON.stringify({
          name: newProfile.name,
          avatarEmoji: newProfile.avatarEmoji,
          avatarColor: newProfile.avatarColor,
          darkMode: newProfile.darkMode,
          notificationsEnabled: newProfile.notificationsEnabled,  // ✅ Save to backend
        }),
      })
      if (res.ok) {
        const data = await res.json()
        setProfile(data)
        setEditName(data.name)
      } else {
        setProfile(profile)
      }
    } catch (err) {
      setProfile(profile)
    } finally {
      setSaving(false)
    }
  }

  const handleToggleNotifications = () => {
    const newValue = !notificationsEnabled
    onToggleNotifications(newValue)
    // ✅ Save to backend
    saveProfile({ notificationsEnabled: newValue })
  }

  const handleLogout = async () => {
    try {
      await authenticatedFetch(`${API_URL}/api/auth/logout`, { method: "POST" })
    } catch (err) {}
    sessionStorage.removeItem("user")
    router.push("/login")
    router.refresh()
  }

  if (fetchError || (!profile && fetchError)) {
    return (
      <button onClick={handleLogout} className="w-10 h-10 rounded-full flex items-center justify-center text-xl bg-orange-500 shadow-md hover:scale-110 transition-transform border-2 border-white">
        🦙
      </button>
    )
  }

  if (!profile) {
    return <div className="w-10 h-10 rounded-full bg-orange-200 animate-pulse flex items-center justify-center text-xl">🦙</div>
  }

  return (
    <div className="relative" ref={dropdownRef}>
      <button onClick={() => setOpen(!open)} className="w-10 h-10 rounded-full flex items-center justify-center text-xl shadow-md hover:scale-110 transition-transform border-2 border-white" style={{ backgroundColor: profile.avatarColor }}>
        {profile.avatarEmoji}
      </button>

      {open && (
        <div className="absolute right-0 top-12 w-80 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl border border-gray-100 dark:border-gray-700 z-50 overflow-hidden">
          <div className="p-5 text-white" style={{ background: `linear-gradient(135deg, ${profile.avatarColor}, ${profile.avatarColor}99)` }}>
            <div className="flex items-center gap-4">
              <button onClick={() => setShowEmojiPicker(!showEmojiPicker)} className="w-16 h-16 rounded-full flex items-center justify-center text-3xl bg-white/20 hover:bg-white/30 transition border-2 border-white/50">
                {profile.avatarEmoji}
              </button>
              <div className="flex-1 min-w-0">
                {editing ? (
                  <input type="text" value={editName} onChange={(e) => setEditName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") { saveProfile({ name: editName }); setEditing(false) }
                      if (e.key === "Escape") { setEditing(false); setEditName(profile.name) }
                    }}
                    className="w-full bg-white/20 text-white rounded-lg px-3 py-1 text-lg font-semibold outline-none border border-white/50 focus:border-white" autoFocus
                  />
                ) : (
                  <button onClick={() => setEditing(true)} className="text-lg font-semibold truncate hover:underline flex items-center gap-1 group">
                    {profile.name}
                    <span className="text-sm opacity-0 group-hover:opacity-100 transition">✏️</span>
                  </button>
                )}
                <p className="text-sm opacity-80 truncate">{profile.email}</p>
                <span className="text-xs px-2 py-0.5 rounded-full font-medium mt-1 inline-block bg-yellow-400 text-yellow-900">
                  {profile.role}
                </span>
              </div>
            </div>

            {showEmojiPicker && (
              <div className="mt-3 bg-white/20 rounded-xl p-3">
                <p className="text-xs text-white/70 mb-2">Choose avatar</p>
                <div className="grid grid-cols-8 gap-1">
                  {AVATAR_EMOJIS.map((emoji) => (
                    <button key={emoji} onClick={() => { saveProfile({ avatarEmoji: emoji }); setShowEmojiPicker(false) }}
                      className={`text-xl p-1 rounded-lg hover:bg-white/30 transition ${profile.avatarEmoji === emoji ? "bg-white/40 ring-2 ring-white" : ""}`}>
                      {emoji}
                    </button>
                  ))}
                </div>
                <p className="text-xs text-white/70 mt-2 mb-1">Choose color</p>
                <div className="flex gap-1 flex-wrap">
                  {AVATAR_COLORS.map((color) => (
                    <button key={color} onClick={() => saveProfile({ avatarColor: color })}
                      className={`w-6 h-6 rounded-full border-2 transition hover:scale-110 ${profile.avatarColor === color ? "border-white scale-110" : "border-transparent"}`}
                      style={{ backgroundColor: color }}
                    />
                  ))}
                </div>
              </div>
            )}

            {editing && (
              <div className="flex gap-2 mt-2">
                <button onClick={() => { saveProfile({ name: editName }); setEditing(false) }} disabled={saving}
                  className="flex-1 bg-white/20 hover:bg-white/30 text-white text-sm py-1 rounded-lg transition">
                  {saving ? "Saving..." : "✅ Save"}
                </button>
                <button onClick={() => { setEditing(false); setEditName(profile.name) }}
                  className="flex-1 bg-white/10 hover:bg-white/20 text-white text-sm py-1 rounded-lg transition">
                  Cancel
                </button>
              </div>
            )}
          </div>

          <div className="p-3 space-y-1">
            {/* Dark Mode Toggle */}
            <div className="flex items-center justify-between px-3 py-2.5 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              <div className="flex items-center gap-3">
                <span className="text-xl">{profile.darkMode ? "🌙" : "☀️"}</span>
                <div>
                  <p className="text-sm font-medium text-gray-800 dark:text-gray-200">{profile.darkMode ? "Dark Mode" : "Light Mode"}</p>
                  <p className="text-xs text-gray-400">Toggle theme</p>
                </div>
              </div>
              <button onClick={() => saveProfile({ darkMode: !profile.darkMode })}
                className={`relative w-12 h-6 rounded-full transition-colors duration-200 ${profile.darkMode ? "bg-blue-500" : "bg-gray-300"}`}>
                <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200 ${profile.darkMode ? "translate-x-6" : "translate-x-0"}`} />
              </button>
            </div>

            {/* Notifications Toggle - Admin Only */}
            <div className="flex items-center justify-between px-3 py-2.5 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              <div className="flex items-center gap-3">
                <span className="text-xl">🔔</span>
                <div>
                  <p className="text-sm font-medium text-gray-800 dark:text-gray-200">Notifications</p>
                  <p className="text-xs text-gray-400">Speech bubble alerts</p>
                </div>
              </div>
              <button onClick={handleToggleNotifications}
                className={`relative w-12 h-6 rounded-full transition-colors duration-200 ${notificationsEnabled ? "bg-orange-500" : "bg-gray-300"}`}>
                <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200 ${notificationsEnabled ? "translate-x-6" : "translate-x-0"}`} />
              </button>
            </div>

            <div className="border-t border-gray-100 dark:border-gray-700 my-2" />

            <button onClick={handleLogout} className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl hover:bg-red-50 dark:hover:bg-red-900/20 transition group">
              <span className="text-xl">🚪</span>
              <div className="text-left">
                <p className="text-sm font-medium text-gray-800 dark:text-gray-200 group-hover:text-red-600 transition">Logout</p>
                <p className="text-xs text-gray-400">{profile.email}</p>
              </div>
            </button>
          </div>

          <div className="px-4 py-2 bg-gray-50 dark:bg-gray-900 border-t border-gray-100 dark:border-gray-700">
            <p className="text-xs text-gray-400 text-center">🦙 Llama Farm v1.0</p>
          </div>
        </div>
      )}
    </div>
  )
}