"use client"

import UserPrinterStatus from "@/components/UserPrinterStatus"
import FilamentUsageChart from "@/components/FilamentUsageChart"

export default function UserDashboardPage() {
  return (
    <div className="flex flex-col gap-6 w-full h-full p-6 bg-gray-50 dark:bg-gray-900 transition-colors duration-200">
      <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">Dashboard</h1>

      <div className="flex flex-col lg:flex-row gap-6 items-start">
        <div className="flex-grow w-full lg:w-2/3">
          <UserPrinterStatus />
        </div>
        <div className="flex-shrink-0 w-full lg:w-1/3 h-[400px]">
          <FilamentUsageChart />
        </div>
      </div>
    </div>
  )
}