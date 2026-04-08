// app/api/logout/route.ts
import { NextResponse } from "next/server"

export async function POST() {
  const res = NextResponse.json({ success: true })

  // Delete the cookie by setting it with maxAge = 0
  res.cookies.set({
    name: "auth",
    value: "",
    path: "/",
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    maxAge: 0, // ⬅️ removes the cookie
  })

  return res
}
