"use client"

import { Suspense, useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"
import { motion } from "framer-motion"
import { BookOpen, Lightbulb } from "lucide-react"
import { AppSidebar } from "@/components/app-sidebar"
import { CoteWiki } from "@/components/cote-wiki"
import { ExperienceVault } from "@/components/experience-vault"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

type VaultView = "experience" | "wiki"

const TABS = [
  { key: "experience" as const, label: "Experience Vault", icon: BookOpen },
  { key: "wiki" as const, label: "Coding Wiki", icon: Lightbulb },
]

const HEADER_INFO: Record<VaultView, { title: string; description: string }> = {
  experience: {
    title: "Experience Vault",
    description: "Upload project experience and connect it to RAG context.",
  },
  wiki: {
    title: "Coding Wiki",
    description: "Organize syntax and algorithm review cards for coding tests.",
  },
}

function VaultPageContent() {
  const searchParams = useSearchParams()
  const requestedView = searchParams.get("view") === "wiki" ? "wiki" : "experience"
  const [view, setView] = useState<VaultView>(requestedView)
  const { title, description } = HEADER_INFO[view]

  useEffect(() => {
    setView(requestedView)
  }, [requestedView])

  return (
    <div className="flex h-screen flex-col">
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-background px-8">
        <div className="flex flex-col">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>

        <div className="relative flex items-center rounded-lg bg-muted p-[3px]">
          {TABS.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              type="button"
              onClick={() => setView(key)}
              className={`relative z-10 flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                view === key
                  ? "text-foreground"
                  : "text-muted-foreground hover:text-foreground/70"
              }`}
            >
              <Icon className="size-4" />
              {label}
              {view === key ? (
                <motion.div
                  layoutId="vault-view-toggle"
                  className="absolute inset-0 rounded-md bg-background shadow-sm"
                  style={{ zIndex: -1 }}
                  transition={{ type: "spring", duration: 0.35, bounce: 0.15 }}
                />
              ) : null}
            </button>
          ))}
        </div>
      </header>

      <main className="flex-1 overflow-auto bg-muted/30 p-6">
        {view === "experience" ? <ExperienceVault /> : <CoteWiki />}
      </main>
    </div>
  )
}

export default function VaultPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <Suspense fallback={<div className="h-screen bg-muted/30" />}>
          <VaultPageContent />
        </Suspense>
      </SidebarInset>
    </SidebarProvider>
  )
}
