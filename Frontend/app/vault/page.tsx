"use client"

import { useState } from "react"
import { motion } from "framer-motion"
import { BookOpen, Code2, Lightbulb } from "lucide-react"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { ExperienceVault } from "@/components/experience-vault"
import { CodingArchive } from "@/components/coding-archive"
import { TechNotes } from "@/components/tech-notes"

type VaultView = "experience" | "coding" | "tech"

const TABS = [
  { key: "experience" as const, label: "경험 보관소", icon: BookOpen },
  { key: "coding" as const, label: "코딩 아카이브", icon: Code2 },
  { key: "tech" as const, label: "기술 노트", icon: Lightbulb },
]

const HEADER_INFO: Record<VaultView, { title: string; desc: string }> = {
  experience: { title: "경험 보관소", desc: "프로젝트 경험 저장 및 관리" },
  coding: { title: "코딩 아카이브", desc: "코딩테스트 문제 복기 및 회고" },
  tech: { title: "기술 노트", desc: "알고리즘 템플릿 벼락치기 노트" },
}

export default function VaultPage() {
  const [view, setView] = useState<VaultView>("experience")
  const { title, desc } = HEADER_INFO[view]

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col">
          <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-background px-8">
            <div>
              <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
              <p className="text-sm text-muted-foreground">{desc}</p>
            </div>

            {/* 탭 토글 */}
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
                  {view === key && (
                    <motion.div
                      layoutId="vault-view-toggle"
                      className="absolute inset-0 rounded-md bg-background shadow-sm"
                      style={{ zIndex: -1 }}
                      transition={{ type: "spring", duration: 0.35, bounce: 0.15 }}
                    />
                  )}
                </button>
              ))}
            </div>
          </header>

          <main className="flex-1 overflow-auto bg-muted/30 p-6">
            {view === "experience" && <ExperienceVault />}
            {view === "coding" && <CodingArchive />}
            {view === "tech" && <TechNotes />}
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
