// app/api/login/route.ts
import { NextResponse } from "next/server"

export async function POST(request: Request) {
  const { email, password } = await request.json()

  // Simple check for demo
  if (email === "admin@example.com" && password === "password123") {
    const res = NextResponse.json({ success: true })

    // Set cookie properly
    res.cookies.set({
      name: "auth",           // cookie name
      value: "true",          // cookie value
      httpOnly: true,         // not accessible from JS
      path: "/",              // valid for entire site
      sameSite: "lax",        // prevents CSRF issues
      secure: process.env.NODE_ENV === "production", // only HTTPS in prod
      maxAge: 60 * 60 * 24,   // expires in 1 day
    })

    return res
  }

  return NextResponse.json({ error: "Invalid credentials" }, { status: 401 })
}
