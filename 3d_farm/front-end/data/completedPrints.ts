export const completedPrints = [
    // We'll manually define a few real-looking samples, then add random ones below
    {
      id: 1,
      printer: "MK4-1",
      startedBy: "Alice",
      startedAt: "2025-09-28T09:32:00Z",
      completedAt: "2025-09-28T11:45:00Z",
      durationMinutes: 133,
      filamentUsedGrams: 92,
    },
    {
      id: 2,
      printer: "Mini-4",
      startedBy: "Bob",
      startedAt: "2025-09-27T15:00:00Z",
      completedAt: "2025-09-27T16:30:00Z",
      durationMinutes: 90,
      filamentUsedGrams: 65,
    },
    {
      id: 3,
      printer: "MK4S-2_SILVER",
      startedBy: "Charlie",
      startedAt: "2025-09-25T08:00:00Z",
      completedAt: "2025-09-25T12:10:00Z",
      durationMinutes: 250,
      filamentUsedGrams: 110,
    },
  ]
  
  // --- Generate extra random data for testing ---
  const printers = [
    "MK4-1", "MK4-2", "MK4-3", "MK4-4",
    "MK4S-1", "MK4S-3_RED", "Mini-1", "Mini-2", "Mini-3", "Mini-4",
  ]
  const users = ["Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Hank"]
  
  for (let i = 4; i <= 10000; i++) {
    const printer = printers[Math.floor(Math.random() * printers.length)]
    const startedBy = users[Math.floor(Math.random() * users.length)]
  
    // pick a random day in the last 365 days
    const daysAgo = Math.floor(Math.random() * 365)
    const start = new Date()
    start.setDate(start.getDate() - daysAgo)
    start.setHours(Math.floor(Math.random() * 24))
    start.setMinutes(Math.floor(Math.random() * 60))
  
    // duration between 30 and 600 minutes
    const durationMinutes = 30 + Math.floor(Math.random() * 570)
    const completedAt = new Date(start.getTime() + durationMinutes * 60000)
  
    // filament between 20g and 200g
    const filamentUsedGrams = 20 + Math.floor(Math.random() * 180)
  
    completedPrints.push({
      id: i,
      printer,
      startedBy,
      startedAt: start.toISOString(),
      completedAt: completedAt.toISOString(),
      durationMinutes,
      filamentUsedGrams,
    })
  }
  