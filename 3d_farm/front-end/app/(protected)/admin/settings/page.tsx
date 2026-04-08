"use client"

import { useState, useEffect } from "react"
import { authenticatedFetch } from "@/lib/auth"
import { toast } from "sonner"

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://192.168.133.223:8080"

type User = {
  id: number
  name: string
  email: string
  role: "ADMIN" | "USER" | "VOLUNTEER" | "ORGANIZER"
  canEstimate: boolean
}

const inputClass = "mt-1 w-full border border-gray-300 dark:border-gray-600 rounded-md px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
const labelClass = "block text-sm font-medium text-gray-700 dark:text-gray-300"
const modalClass = "bg-white dark:bg-gray-800 p-6 rounded-xl shadow-lg w-[400px] border border-gray-200 dark:border-gray-700"
const cancelBtnClass = "px-4 py-2 rounded-md bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 hover:bg-gray-300 dark:hover:bg-gray-600 transition"

export default function SettingsPage() {
  const [currentUser, setCurrentUser] = useState<User | null>(null)
  const [allUsers, setAllUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [newPassword, setNewPassword] = useState("")
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [removingUser, setRemovingUser] = useState<User | null>(null)
  const [addingUser, setAddingUser] = useState<Partial<User> | null>(null)

  useEffect(() => {
    loadCurrentUser()
    loadAllUsers()
  }, [])

  const loadCurrentUser = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/auth/me`)
      const data = await res.json()
      setCurrentUser({
        id: data.userId || 0,
        name: data.name || data.email,
        email: data.email,
        role: data.role,
        canEstimate: true,
      })
    } catch (err) {
      console.error("Failed to load current user:", err)
    }
  }

  const loadAllUsers = async () => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/users`)
      if (res.ok) {
        const data = await res.json()
        setAllUsers(data)
      }
    } catch (err) {
      console.error("Failed to load users:", err)
    } finally {
      setLoading(false)
    }
  }

  const handleToggleEstimateAccess = async (userId: number, currentStatus: boolean) => {
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/users/${userId}/estimate-access`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ canEstimate: !currentStatus }),
      })
      if (res.ok) {
        setAllUsers(prev => prev.map(u => 
          u.id === userId ? { ...u, canEstimate: !currentStatus } : u
        ))
      }
    } catch (err) {
      console.error("Failed to toggle estimate access:", err)
    }
  }

  const handleSaveEdit = async () => {
    if (!editingUser) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/users/${editingUser.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: editingUser.name,
          email: editingUser.email,
          role: editingUser.role,
        }),
      })
      if (res.ok) {
        setAllUsers(prev => prev.map(u => u.id === editingUser.id ? editingUser : u))
        setEditingUser(null)
      }
    } catch (err) {
      console.error("Failed to update user:", err)
    }
  }

  const handleConfirmRemove = async () => {
    if (!removingUser) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/users/${removingUser.id}`, {
        method: "DELETE",
      })
      if (res.ok) {
        setAllUsers(prev => prev.filter(u => u.id !== removingUser.id))
        setRemovingUser(null)
      }
    } catch (err) {
      console.error("Failed to remove user:", err)
    }
  }

  const handleAddUser = async () => {
    if (!addingUser || !addingUser.email || !addingUser.name) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/users`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: addingUser.name,
          email: addingUser.email,
          role: addingUser.role || "USER",
          password: (addingUser as any).password,
        }),
      })
      if (res.ok) {
        await loadAllUsers()
        setAddingUser(null)
      }
    } catch (err) {
      console.error("Failed to add user:", err)
    }
  }

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!currentUser) return
    try {
      const body: Record<string, string> = { name: currentUser.name, email: currentUser.email }
      if (newPassword) body.password = newPassword
      const res = await authenticatedFetch(`${API_URL}/api/profile`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(await res.text())
      toast.success("Settings saved")
      setNewPassword("")
    } catch (err) {
      toast.error("Failed to save settings: " + err)
    }
  }

  const handleResetPassword = async (userId: number, userName: string) => {
    if (!confirm(`Reset password for ${userName}? They will receive an email with a new temporary password.`)) return
    try {
      const res = await authenticatedFetch(`${API_URL}/api/admin/users/${userId}/reset-password`, {
        method: "POST",
      })
      if (res.ok) {
        toast.success(`Password reset email sent to ${userName}`)
      }
    } catch (err) {
      console.error("Failed to reset password:", err)
    }
  }

  if (loading) return <div className="p-6 text-gray-500 dark:text-gray-400">Loading...</div>

  const isAdmin = currentUser?.role === "ADMIN"

  return (
    <div className="p-6 w-full h-full bg-gray-50 dark:bg-gray-900 flex flex-col gap-6 transition-colors duration-200">

      {isAdmin ? (
        <div className="bg-white dark:bg-gray-800 p-6 rounded-xl shadow-md transition-colors duration-200">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-semibold text-gray-800 dark:text-gray-100">User Management</h2>
            <button
              onClick={() => setAddingUser({ name: "", email: "", role: "USER", canEstimate: true })}
              className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition"
            >
              + Add User
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
              <thead>
                <tr className="bg-gray-100 dark:bg-gray-700 text-left">
                  {["Name", "Email", "Role", "Estimate Access", "Actions"].map(h => (
                    <th key={h} className="px-4 py-2 text-sm font-medium text-gray-600 dark:text-gray-300 whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {allUsers.map(u => (
                  <tr key={u.id} className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                    <td className="px-4 py-2 text-gray-800 dark:text-gray-200">{u.name}</td>
                    <td className="px-4 py-2 text-gray-700 dark:text-gray-300">{u.email}</td>
                    <td className="px-4 py-2 capitalize text-gray-700 dark:text-gray-300">
                      {u.role.toLowerCase()}
                    </td>
                    <td className="px-4 py-2">
                      {u.role === "ADMIN" ? (
                        <span className="text-gray-400 dark:text-gray-500 text-sm italic">Always allowed</span>
                      ) : (
                        <button
                          onClick={() => handleToggleEstimateAccess(u.id, u.canEstimate)}
                          className={`px-3 py-1 rounded-full text-xs font-medium transition ${
                            u.canEstimate
                              ? "bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300 hover:bg-green-200 dark:hover:bg-green-900/60"
                              : "bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-300 hover:bg-red-200 dark:hover:bg-red-900/60"
                          }`}
                        >
                          {u.canEstimate ? "✓ Allowed" : "✗ Blocked"}
                        </button>
                      )}
                    </td>
                    <td className="px-4 py-2">
                      <div className="flex gap-3 flex-wrap">
                        <button
                          onClick={() => setEditingUser(u)}
                          className="text-blue-600 dark:text-blue-400 hover:underline text-sm"
                        >
                          Edit
                        </button>
                        {u.role !== "ADMIN" && (
                          <>
                            <button
                              onClick={() => setRemovingUser(u)}
                              className="text-red-600 dark:text-red-400 hover:underline text-sm"
                            >
                              Remove
                            </button>
                            <button
                              onClick={() => handleResetPassword(u.id, u.name)}
                              className="text-yellow-600 dark:text-yellow-400 hover:underline text-sm"
                            >
                              Reset Password
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="bg-white dark:bg-gray-800 p-6 rounded-xl shadow-md w-full max-w-lg transition-colors duration-200">
          <h2 className="text-xl font-semibold mb-4 text-gray-800 dark:text-gray-100">Your Settings</h2>
          <form className="space-y-4" onSubmit={handleSaveProfile}>
            <div>
              <label className={labelClass}>Name</label>
              <input type="text" value={currentUser?.name || ""}
                onChange={e => currentUser && setCurrentUser({ ...currentUser, name: e.target.value })}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Email</label>
              <input type="email" value={currentUser?.email || ""}
                onChange={e => currentUser && setCurrentUser({ ...currentUser, email: e.target.value })}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Password</label>
              <input type="password" placeholder="Leave blank to keep current" value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                className={inputClass}
              />
            </div>
            <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition">
              Save Changes
            </button>
          </form>
        </div>
      )}

      {addingUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={modalClass}>
            <h3 className="text-lg font-semibold mb-4 text-gray-800 dark:text-gray-100">Add New User</h3>
            <div className="space-y-3">
              {[
                { label: "Name", key: "name", type: "text" },
                { label: "Email", key: "email", type: "email" },
                { label: "Password", key: "password", type: "password" },
              ].map(({ label, key, type }) => (
                <div key={key}>
                  <label className={labelClass}>{label}</label>
                  <input type={type} value={(addingUser as any)[key] || ""}
                    onChange={e => setAddingUser({ ...addingUser, [key]: e.target.value })}
                    className={inputClass}
                  />
                </div>
              ))}
              <div>
                <label className={labelClass}>Role</label>
                <select value={addingUser.role || "USER"}
                  onChange={e => setAddingUser({ ...addingUser, role: e.target.value as any })}
                  className={inputClass}
                >
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => setAddingUser(null)} className={cancelBtnClass}>Cancel</button>
              <button onClick={handleAddUser} className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 transition">
                Add
              </button>
            </div>
          </div>
        </div>
      )}

      {editingUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={modalClass}>
            <h3 className="text-lg font-semibold mb-4 text-gray-800 dark:text-gray-100">
              Edit User – {editingUser.name}
            </h3>
            <div className="space-y-3">
              {[
                { label: "Name", key: "name", type: "text" },
                { label: "Email", key: "email", type: "email" },
              ].map(({ label, key, type }) => (
                <div key={key}>
                  <label className={labelClass}>{label}</label>
                  <input type={type} value={(editingUser as any)[key] || ""}
                    onChange={e => setEditingUser({ ...editingUser, [key]: e.target.value })}
                    className={inputClass}
                  />
                </div>
              ))}
              <div>
                <label className={labelClass}>Role</label>
                <select value={editingUser.role}
                  onChange={e => setEditingUser({ ...editingUser, role: e.target.value as any })}
                  className={inputClass}
                >
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => setEditingUser(null)} className={cancelBtnClass}>Cancel</button>
              <button onClick={handleSaveEdit} className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 transition">
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {removingUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={modalClass}>
            <h3 className="text-lg font-semibold mb-4 text-gray-800 dark:text-gray-100">Remove User</h3>
            <p className="text-gray-700 dark:text-gray-300">
              Are you sure you want to remove <strong>{removingUser.name}</strong>?
            </p>
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => setRemovingUser(null)} className={cancelBtnClass}>Cancel</button>
              <button onClick={handleConfirmRemove} className="px-4 py-2 rounded-md bg-red-600 text-white hover:bg-red-700 transition">
                Remove
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}