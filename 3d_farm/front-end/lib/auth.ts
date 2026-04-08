// lib/auth.ts

const API_URL = process.env.NEXT_PUBLIC_API_URL

export type User = {
  email: string
  role: string
  roles: string[]
  userId: number
  name: string
  isAdmin: boolean
  avatarEmoji?: string
  avatarColor?: string
  darkMode?: boolean
  notificationsEnabled?: boolean
}

// Login with rate limit error handling
export async function login(email: string, password: string): Promise<boolean> {
  try {
    const res = await fetch(`${API_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ email, password }),
    })
    
    // Handle rate limiting (429)
    if (res.status === 429) {
      const errorData = await res.json()
      throw new Error(errorData.error || 'Too many login attempts. Please try again later.')
    }
    
    return res.ok
  } catch (error) {
    console.error('Login error:', error)
    throw error // Re-throw to let the UI handle it
  }
}

export async function authenticatedFetch(url: string, options: RequestInit = {}) {
  const isFormData = options.body instanceof FormData

  return fetch(url, {
    ...options,
    credentials: 'include',
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    },
  })
}

export async function register(
  name: string,
  email: string,
  password: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const res = await fetch(`${API_URL}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ name, email, password }),
    })

    if (!res.ok) {
      const errorText = await res.text()
      return { success: false, error: errorText }
    }

    return { success: true }
  } catch (error) {
    console.error('Register error:', error)
    return { success: false, error: 'Network error' }
  }
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    const res = await fetch(`${API_URL}/api/auth/me`, {
      credentials: 'include',
    })
    if (!res.ok) return null
    return await res.json()
  } catch (error) {
    console.error('Get user error:', error)
    return null
  }
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${API_URL}/api/auth/logout`, {
      method: 'POST',
      credentials: 'include',
    })
  } catch (error) {
    console.error('Logout error:', error)
  }
}

export function isAdmin(user: User | null): boolean {
  return user?.role === 'ADMIN'
}

export const authFetch = authenticatedFetch