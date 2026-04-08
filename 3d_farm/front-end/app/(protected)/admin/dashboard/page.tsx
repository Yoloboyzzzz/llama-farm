"use client"

import FilamentUsageChart from "@/components/FilamentUsageChart"
import PrinterStatusOverview from "@/components/PrinterStatusOverview"

export default function DashboardPage() {
  return (
    // Change bg-gray-50 to bg-white to at least make the frame invisible
<div className="flex flex-col gap-6 w-full h-full p-6 bg-white dark:bg-gray-900 transition-colors duration-200">

      <div className="flex flex-col lg:flex-row gap-6 items-start">
        <div className="flex-grow w-full lg:w-2/3">
          <PrinterStatusOverview />
        </div>
        <div className="flex-shrink-0 w-full lg:w-1/3 h-[400px]">
          <FilamentUsageChart />
        </div>
      </div>
    </div>
  )
}