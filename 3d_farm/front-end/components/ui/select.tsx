"use client"

import { useState } from "react"
import { cn } from "@/lib/utils"

export function Select({
  value,
  onValueChange,
  children,
}: {
  value: string
  onValueChange: (val: string) => void
  children: React.ReactNode
}) {
  return <div>{children}</div>
}

export function SelectTrigger({
  className,
  children,
}: {
  className?: string
  children: React.ReactNode
}) {
  return (
    <div
      className={cn(
        "flex h-10 w-full items-center justify-between rounded border border-gray-300 bg-white px-3 py-2 text-sm",
        className
      )}
    >
      {children}
    </div>
  )
}

export function SelectValue({ placeholder }: { placeholder?: string }) {
  return <span className="text-gray-500">{placeholder}</span>
}

export function SelectContent({ children }: { children: React.ReactNode }) {
  return <div className="mt-2 rounded border bg-white shadow">{children}</div>
}

export function SelectItem({
  value,
  children,
  onSelect,
}: {
  value: string
  children: React.ReactNode
  onSelect?: (value: string) => void
}) {
  return (
    <div
      className="cursor-pointer px-3 py-2 hover:bg-gray-100"
      onClick={() => onSelect?.(value)}
    >
      {children}
    </div>
  )
}
