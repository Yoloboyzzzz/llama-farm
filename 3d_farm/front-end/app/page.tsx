"use client"

import Link from "next/link"
import Image from "next/image"
import { useEffect, useState, useRef } from "react"

export default function LandingPage() {
  const [spitting, setSpitting] = useState(false)
  const [showSpitEffect, setShowSpitEffect] = useState(false)
  const [backgroundLlamas, setBackgroundLlamas] = useState<Array<{left: string, top: string, rotate: string}>>([])
  const audioRef = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    // Only run on client
    if (typeof window === 'undefined') return
  
    // Generate random background llama positions on client only
    setBackgroundLlamas(
      Array.from({ length: 20 }, () => ({
        left: `${Math.random() * 100}%`,
        top: `${Math.random() * 100}%`,
        rotate: `${Math.random() * 360}deg`,
      }))
    )

    const llamaField = document.getElementById('llamaField')
    if (!llamaField) return
  
    // Spawn floating llamas randomly
    const llamaImages = [
      '/Lamas/Lama1.png',
      '/Lamas/Lama2.png',
      '/Lamas/Lama3.png',
      '/Lamas/Lama4.png',
      '/Lamas/Lama5.png',
      '/Lamas/Lama6.png',
      '/Lamas/Lama7.png',
      '/Lamas/Lama8.png',
      '/Lamas/Lama9.png',
      '/Lamas/Lama10.png',
      '/Lamas/Lama11.png',
      '/Lamas/Lama12.png',
      '/Lamas/Lama13.png',
      '/Lamas/Lama14.png'
    ]
    
    const spawnLlama = () => {
      const llama = document.createElement('img')
      llama.className = 'floating-llama'
      llama.src = llamaImages[Math.floor(Math.random() * llamaImages.length)]
      llama.style.left = Math.random() * 100 + 'vw'
      llama.style.width = (40 + Math.random() * 80) + 'px' // Random size 40-120px
      llama.style.animationDuration = (15 + Math.random() * 15) + 's'
      llamaField.appendChild(llama)
  
      setTimeout(() => llama.remove(), 30000)
    }
  
    const llamaInterval = setInterval(() => {
      if (Math.random() > 0.4) spawnLlama()
    }, 3000)
  
    // Initialize audio
    audioRef.current = new Audio('https://upload.wikimedia.org/wikipedia/commons/3/3c/Llama.ogg')
    audioRef.current.volume = 0.25
  
    // Play random llama sounds
    const playLlamaSound = () => {
      if (audioRef.current && !document.hidden) {
        audioRef.current.play().catch(() => {})
      }
      setTimeout(playLlamaSound, 25000 + Math.random() * 50000)
    }
    const soundTimeout = setTimeout(playLlamaSound, 8000)
  
    // Random spitting animation
    const spitInterval = setInterval(() => {
      if (Math.random() > 0.75) {
        setSpitting(true)
        setShowSpitEffect(true)
        if (audioRef.current) {
          const spitSound = audioRef.current.cloneNode() as HTMLAudioElement
          spitSound.volume = 0.3
          spitSound.playbackRate = 1.2
          spitSound.play().catch(() => {})
        }
        setTimeout(() => {
          setSpitting(false)
          setTimeout(() => setShowSpitEffect(false), 500)
        }, 1500)
      }
    }, 12000)
  
    // Cleanup
    return () => {
      clearInterval(llamaInterval)
      clearInterval(spitInterval)
      clearTimeout(soundTimeout)
      if (llamaField) llamaField.innerHTML = ''
    }
  }, [])

  return (
    <>
      <style jsx global>{`
        @import url('https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;800&family=Inter:wght@400;500&display=swap');
        
        .floating-llama {
          position: fixed;
          opacity: 0.3;
          pointer-events: none;
          animation: float-up linear forwards;
          z-index: 1;
          filter: drop-shadow(2px 2px 4px rgba(0,0,0,0.2));
          object-fit: contain;
        }
        
        @keyframes float-up {
          from {
            transform: translateY(110vh) rotate(0deg);
            opacity: 0;
          }
          10% { opacity: 0.25; }
          90% { opacity: 0.15; }
          to {
            transform: translateY(-20vh) rotate(360deg);
            opacity: 0;
          }
        }

        .spit-animation {
          animation: spit 1.5s ease-out forwards;
        }

        @keyframes spit {
          0% { transform: scale(0) translateX(0); opacity: 1; }
          50% { transform: scale(1.5) translateX(200px) translateY(-50px); opacity: 0.7; }
          100% { transform: scale(0.5) translateX(400px) translateY(-100px); opacity: 0; }
        }

        .spit-animation-left {
          animation: spit-left 1.5s ease-out forwards;
        }

        @keyframes spit-left {
          0% { transform: scale(0) translateX(0); opacity: 1; }
          50% { transform: scale(1.5) translateX(-200px) translateY(-50px); opacity: 0.7; }
          100% { transform: scale(0.5) translateX(-400px) translateY(-100px); opacity: 0; }
        }

        .llama-wiggle {
          animation: wiggle 0.5s ease-in-out 3;
        }

        @keyframes wiggle {
          0%, 100% { transform: rotate(0deg); }
          25% { transform: rotate(-5deg); }
          75% { transform: rotate(5deg); }
        }
      `}</style>

      <div id="llamaField" className="fixed inset-0 pointer-events-none" style={{ zIndex: 1 }}></div>

      <div className="min-h-screen bg-gradient-to-b from-amber-50 to-white" style={{ fontFamily: 'Inter, system-ui, sans-serif' }}>
        
        {/* Header */}
        <header className="fixed top-0 left-0 right-0 z-50 bg-white/90 backdrop-blur-md border-b border-amber-200 shadow-sm">
          <div className="max-w-7xl mx-auto px-6 py-3 flex justify-between items-center">
            <div className="flex items-center gap-3">
              <Image 
                src="/LLAMA.jpg" 
                alt="Llama Farm Logo" 
                width={60} 
                height={60}
                className={`object-contain ${spitting ? 'llama-wiggle' : ''}`}
              />
              <div>
                <h1 className="text-2xl font-bold text-gray-800" style={{ fontFamily: 'Montserrat' }}>
                  LLAMA Farm
                </h1>
                <p className="text-xs text-amber-600 font-medium">
                  Large-scale Layered Additive Manufacturing Automation
                </p>
              </div>
            </div>
            
            <Link 
              href="/login"
              className="px-6 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 transition-all hover:scale-105 shadow-md font-semibold"
            >
              Join the Herd 🦙
            </Link>
          </div>
        </header>

        {/* Spitting Effect Overlay */}
        {showSpitEffect && (
          <div className="fixed top-20 left-20 z-40 spit-animation">
            <div className="text-6xl">💦</div>
          </div>
        )}

        {/* Hero Section */}
        <section className="min-h-screen flex items-center justify-center pt-20 px-6 relative overflow-hidden">
          <div className="absolute inset-0 opacity-5">
            {backgroundLlamas.map((llama, i) => (
              <div
                key={i}
                className="absolute text-9xl"
                style={{
                  left: llama.left,
                  top: llama.top,
                  transform: `rotate(${llama.rotate})`,
                }}
              >
                🦙
              </div>
            ))}
          </div>

          <div className="text-center max-w-5xl relative z-10">
            <div className="mb-8 flex justify-center gap-8">
              <div className="relative">
                <Image 
                  src="/Henry_Llama_same_size.png" 
                  alt="LLAMA - Large-scale Layered Additive Manufacturing Automation" 
                  width={300} 
                  height={300}
                  className={`object-contain ${spitting ? 'llama-wiggle' : ''}`}
                  priority
                />
                {spitting && (
                  <div className="absolute -right-10 top-1/2 text-4xl spit-animation">
                    💦💦💦
                  </div>
                )}
              </div>
              <div className="relative">
                <Image 
                  src="/Marc_Llama_same_size.png" 
                  alt="LLAMA Farm Logo" 
                  width={300} 
                  height={300}
                  className={`object-contain ${spitting ? 'llama-wiggle' : ''}`}
                  priority
                />
                {spitting && (
                  <div className="absolute -left-10 top-1/2 text-4xl spit-animation-left">
                    💦💦💦
                  </div>
                )}
              </div>
            </div>
            
            <h2 className="text-7xl font-extrabold mb-4" style={{ fontFamily: 'Montserrat' }}>
              <span className="text-orange-600">LLAMA</span> Print Farm
            </h2>

            <p className="text-2xl text-gray-700 font-semibold mb-3">
              Large-scale Layered Additive Manufacturing Automation
            </p>
            
            <p className="text-4xl text-amber-700 font-bold mb-6 italic">
              "Printers work better in herds" 🦙
            </p>

            <p className="text-xl text-gray-600 mb-4 max-w-3xl mx-auto leading-relaxed">
              Born at <strong className="text-orange-600">FabLab Leuven</strong>, raised by a student, a bio-chemist, coffee and way too many paprika nuts.
              Just like llamas in nature, your 3D printers thrive when coordinated as a herd.
            </p>

            <div className="mb-8 flex justify-center">
              <Image 
                src="/logo_gif.gif"
                alt="Llama Herd Animation"
                width={400}
                height={300}
                className="rounded-lg shadow-2xl border-4 border-amber-400"
                unoptimized
              />
            </div>
            
            <div className="flex gap-4 justify-center flex-wrap">
              <Link 
                href="/login"
                className="px-10 py-4 bg-orange-600 text-white text-xl rounded-xl hover:bg-orange-700 transition-all hover:scale-105 shadow-xl font-bold"
              >
                🦙 Join the Herd
              </Link>
              
              <Link 
                href="/user/estimate"
                className="px-10 py-4 bg-blue-600 text-white text-xl rounded-xl hover:bg-blue-700 transition-all hover:scale-105 shadow-xl font-bold"
              >
                💰 Get Estimate
              </Link>

              <a
                href="https://github.com/yourusername/llama-farm"
                target="_blank"
                rel="noopener noreferrer"
                className="px-10 py-4 bg-gray-900 text-white text-xl rounded-xl hover:bg-gray-800 transition-all hover:scale-105 shadow-xl font-bold"
              >
                📂 View on GitHub
              </a>
            </div>
          </div>
        </section>

        {/* What is a Print Farm */}
        <section className="py-20 px-6 bg-white">
          <div className="max-w-7xl mx-auto">
            <div className="text-center mb-16">
              <div className="text-7xl mb-4">🏭</div>
              <h2 className="text-5xl font-extrabold mb-6 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                What is a 3D Printer Farm?
              </h2>
              <p className="text-2xl text-gray-600 max-w-4xl mx-auto">
                A coordinated herd of 3D printers working together under one watchful llama 👀
              </p>
            </div>

            <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8 mb-12">
              {[
                { emoji: '📦', title: 'Job Intake', desc: 'Upload your STL files. LLAMA validates them and adds them to the herd queue.' },
                { emoji: '🧮', title: 'Smart Nesting', desc: 'AI arranges multiple objects on build plates to maximize efficiency. No wasted space!' },
                { emoji: '📋', title: 'Queue & Scheduling', desc: 'Jobs are prioritized by deadline and assigned to the right printer automatically.' },
                { emoji: '🖨️', title: 'Printer Monitoring', desc: 'Live status updates from every printer. When one alerts, the herd responds.' },
              ].map((item, i) => (
                <div key={i} className="bg-gradient-to-br from-amber-50 to-orange-50 rounded-2xl p-8 shadow-lg border-2 border-amber-200 hover:shadow-2xl transition-all hover:-translate-y-1">
                  <div className="text-6xl mb-4 text-center">{item.emoji}</div>
                  <h3 className="text-2xl font-bold mb-3 text-gray-900 text-center" style={{ fontFamily: 'Montserrat' }}>
                    {item.title}
                  </h3>
                  <p className="text-gray-700 text-center leading-relaxed">{item.desc}</p>
                </div>
              ))}
            </div>

            <div className="bg-gradient-to-r from-amber-100 to-orange-100 rounded-3xl p-12 border-4 border-amber-300">
              <div className="flex items-start gap-6">
                <Image src="/LLAMA.jpg" alt="Llama" width={120} height={120} className="flex-shrink-0" />
                <div>
                  <h3 className="text-3xl font-bold mb-4 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                    📊 Traceability & Analytics
                  </h3>
                  <p className="text-xl text-gray-700 leading-relaxed">
                    Know exactly who printed what, when, on which machine, with which material, and why it succeeded or failed.
                    Track filament usage, success rates, and machine uptime. LLAMA remembers everything the herd does.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* LLAMA Philosophy */}
        <section className="py-20 px-6 bg-gradient-to-b from-white to-amber-50">
          <div className="max-w-7xl mx-auto">
            <div className="text-center mb-16">
              <div className="flex justify-center gap-4 mb-6">
                <span className="text-7xl">🦙</span>
                <span className="text-7xl">🧠</span>
                <span className="text-7xl">🦙</span>
              </div>
              <h2 className="text-5xl font-extrabold mb-6 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                The LLAMA Philosophy
              </h2>
              <p className="text-2xl text-gray-600 max-w-4xl mx-auto">
                Running a printer farm shouldn't feel like herding angry cats. LLAMA makes it calm, transparent, and scalable.
              </p>
            </div>

            <div className="grid md:grid-cols-3 gap-10">
              {[
                { 
                  emoji: '🧠', 
                  title: 'Automation with Insight', 
                  desc: "LLAMA orchestrates printers, queues, users, and materials — without hiding what's going on. You always know what the herd is doing.",
                  color: 'from-blue-500 to-indigo-600'
                },
                { 
                  emoji: '🛠️', 
                  title: 'Built in a FabLab', 
                  desc: 'Developed at FabLab Leuven based on real lab workflows, real failures, and real people. Not theory — actual herd management.',
                  color: 'from-green-500 to-teal-600'
                },
                { 
                  emoji: '🌱', 
                  title: 'Open & Shareable', 
                  desc: 'Open-source friendly, community-driven, and designed to grow with your farm. Licensed under CC BY-NC-SA 4.0 — the herd belongs to everyone.',
                  color: 'from-purple-500 to-pink-600'
                },
              ].map((item, i) => (
                <div key={i} className="relative">
                  <div className="bg-white rounded-3xl p-10 shadow-2xl border-4 border-amber-200 hover:shadow-3xl transition-all hover:-translate-y-2 h-full">
                    <div className={`absolute -top-8 left-1/2 transform -translate-x-1/2 w-16 h-16 bg-gradient-to-br ${item.color} rounded-full flex items-center justify-center text-3xl shadow-lg`}>
                      {item.emoji}
                    </div>
                    <div className="pt-8">
                      <h3 className="text-2xl font-bold mb-4 text-gray-900 text-center" style={{ fontFamily: 'Montserrat' }}>
                        {item.title}
                      </h3>
                      <p className="text-gray-700 leading-relaxed text-center">{item.desc}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Why Llamas */}
        <section className="py-20 px-6 bg-amber-50">
          <div className="max-w-7xl mx-auto">
            <div className="text-center mb-16">
              <h2 className="text-5xl font-extrabold mb-6 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                Why Llamas? 🦙 (The Science)
              </h2>
              <p className="text-2xl text-gray-600 max-w-4xl mx-auto">
                This isn't just cute naming — it's biologically accurate and conceptually brilliant
              </p>
            </div>

            <div className="grid md:grid-cols-3 gap-10 mb-12">
              {[
                { title: 'Social Animals', desc: 'Llamas naturally live in herds with one dominant male coordinating the group. Just like your printers need one system orchestrating them.' },
                { title: 'Herd Communication', desc: 'They communicate through posture, ear position, and vocalizations. Your printers communicate through status updates and alerts — very distributed system vibes.' },
                { title: 'Guard Instincts', desc: 'Llamas are famously used as guard animals because of their strong herd awareness. LLAMA guards your print farm the same way.' },
              ].map((item, i) => (
                <div key={i} className="bg-white rounded-2xl p-8 shadow-xl border-2 border-amber-300">
                  <div className="flex justify-center mb-6">
                    <Image src="/LLAMA.jpg" alt={item.title} width={100} height={100} />
                  </div>
                  <h3 className="text-2xl font-bold mb-4 text-gray-900 text-center" style={{ fontFamily: 'Montserrat' }}>
                    {item.title}
                  </h3>
                  <p className="text-gray-700 leading-relaxed text-center">{item.desc}</p>
                </div>
              ))}
            </div>

            <div className="bg-gradient-to-r from-orange-400 to-amber-500 rounded-3xl p-12 text-center text-white shadow-2xl">
              <div className="flex justify-center gap-4 mb-6">
                {[...Array(7)].map((_, i) => (
                  <Image key={i} src="/LLAMA.jpg" alt="Llama" width={60} height={60}/>
                ))}
              </div>
              <p className="text-4xl font-bold mb-4" style={{ fontFamily: 'Montserrat' }}>
                Your print farm = herd
              </p>
              <p className="text-2xl">
                Printers working together • One system coordinating • A llama watching over all
              </p>
            </div>
          </div>
        </section>

        {/* Member Benefits */}
        <section className="py-20 px-6 bg-white">
          <div className="max-w-7xl mx-auto">
            <div className="text-center mb-16">
              <div className="text-7xl mb-4">🎉</div>
              <h2 className="text-5xl font-extrabold mb-6 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                Join the Herd — Here's What You Get
              </h2>
            </div>

            <div className="grid md:grid-cols-2 gap-10">
              <div className="bg-gradient-to-br from-blue-50 to-indigo-50 rounded-3xl p-10 shadow-xl border-4 border-blue-200">
                <div className="text-6xl mb-6">📊</div>
                <h3 className="text-3xl font-bold mb-4 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                  Live Dashboard Access
                </h3>
                <p className="text-xl text-gray-700 leading-relaxed mb-4">
                  See the entire herd in real-time. Monitor printer status, queue length, current jobs, and filament usage.
                  Watch the farm work like a well-coordinated llama pack.
                </p>
                <Link 
                  href="/login"
                  className="inline-block px-8 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-bold text-lg"
                >
                  View Live Dashboard →
                </Link>
              </div>

              <div className="bg-gradient-to-br from-green-50 to-emerald-50 rounded-3xl p-10 shadow-xl border-4 border-green-200">
                <div className="text-6xl mb-6">💰</div>
                <h3 className="text-3xl font-bold mb-4 text-gray-900" style={{ fontFamily: 'Montserrat' }}>
                  Instant Cost Estimates
                </h3>
                <p className="text-xl text-gray-700 leading-relaxed mb-4">
                  Upload your STL and get an instant estimate of material weight, print time, and cost.
                  Know before you print — no surprises.
                </p>
                <Link 
                  href="/user/estimate"
                  className="inline-block px-8 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 transition font-bold text-lg"
                >
                  Get Estimate →
                </Link>
              </div>
            </div>
          </div>
        </section>

        {/* Open Source CTA */}
        <section className="py-20 px-6 bg-gradient-to-r from-gray-900 via-gray-800 to-gray-900">
          <div className="max-w-6xl mx-auto text-center">
            <div className="flex justify-center gap-6 mb-8">
              {[...Array(5)].map((_, i) => (
                <Image key={i} src="/LLAMA.jpg" alt="Llama" width={80} height={80} />
              ))}
            </div>
            <h2 className="text-5xl font-extrabold text-white mb-6" style={{ fontFamily: 'Montserrat' }}>
              🤝 Build LLAMA Together
            </h2>
            <p className="text-2xl text-gray-300 mb-6 max-w-3xl mx-auto leading-relaxed">
              LLAMA is developed openly and collaboratively. The source code lives on GitHub —
              contributions, forks, and discussions are welcome. The herd grows stronger together.
            </p>
            <div className="bg-amber-100 inline-block rounded-2xl px-8 py-4 mb-8">
              <p className="text-gray-900 text-lg font-semibold">
                📜 Licensed under <strong>Creative Commons BY-NC-SA 4.0</strong>
              </p>
              <p className="text-gray-700 text-sm">
                Attribution • Non-Commercial • Share-Alike
              </p>
            </div>
            <div className="flex gap-6 justify-center flex-wrap">
              <a
                href="https://github.com/yourusername/llama-farm"
                target="_blank"
                rel="noopener noreferrer"
                className="px-10 py-4 bg-white text-gray-900 text-xl rounded-xl hover:bg-gray-100 transition-all hover:scale-105 shadow-xl font-bold"
              >
                📂 GitHub Repository
              </a>
              <Link
                href="/login"
                className="px-10 py-4 bg-orange-600 text-white text-xl rounded-xl hover:bg-orange-700 transition-all hover:scale-105 shadow-xl font-bold"
              >
                🦙 Join the Herd
              </Link>
            </div>
          </div>
        </section>

        {/* Footer */}
        <footer className="py-16 px-6 bg-gray-900 text-gray-400">
          <div className="max-w-7xl mx-auto">
            <div className="grid md:grid-cols-3 gap-12 mb-12">
              <div className="text-center md:text-left">
                <div className="flex items-center justify-center md:justify-start gap-3 mb-4">
                  <Image src="/LLama_Logo.jpg" alt="Llama" width={60} height={60}/>
                  <div>
                    <div className="text-2xl font-bold text-white" style={{ fontFamily: 'Montserrat' }}>LLAMA Farm</div>
                    <div className="text-xs text-amber-400">Printers work better in herds</div>
                  </div>
                </div>
                <p className="text-sm">
                  Large-scale Layered Additive<br />Manufacturing Automation
                </p>
              </div>

              <div className="text-center">
                <h4 className="text-white font-bold mb-4">Born at FabLab Leuven</h4>
                <p className="text-sm leading-relaxed">
                  Developed by a student who understands the chaos of managing
                  multiple 3D printers. Built with paprika nuts, determination, and llama wisdom.
                </p>
              </div>

              <div className="text-center md:text-right">
                <h4 className="text-white font-bold mb-4">The Herd</h4>
                <p className="text-sm">
                  Sometimes LLAMA makes a sound.<br />
                  That's normal. 🦙<br />
                  <span className="text-amber-400 italic">*spitting noises*</span>
                </p>
              </div>
            </div>

            <div className="border-t border-gray-700 pt-8 text-center">
              <p className="text-sm text-gray-500">
                © 2026 LLAMA Farm • Made with 🦙 energy at FabLab Leuven
              </p>
              <p className="text-xs text-gray-600 mt-2">
                Not just cute — biologically accurate and conceptually brilliant
              </p>
            </div>
          </div>
        </footer>
      </div>
    </>
  )
}