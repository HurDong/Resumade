"use client"

import { useState } from "react"
import { motion } from "framer-motion"
import { LayoutGrid, CalendarDays } from "lucide-react"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { KanbanBoard } from "@/components/kanban-board"
import { RecruitmentCalendar } from "@/components/recruitment-calendar"
import { GlobalRecruitmentCalendar } from "@/components/global-recruitment-calendar"

type DashboardView = "kanban" | "calendar" | "global"

export default function DashboardPage() {
  const [view, setView] = useState<DashboardView>("kanban")

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0 min-w-0">
        <div className="flex h-screen min-h-0 flex-col">
          <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-background px-6">
            <h1 className="text-lg font-semibold tracking-tight">
              {view === "kanban" ? "내 파이프라인 (Kanban)" : view === "calendar" ? "내 지원 달력" : "전체 채용 공고 탐색"}
            </h1>

            {/* 뷰 토글 */}
            <div className="relative flex items-center rounded-lg bg-muted p-[3px]">
              {(
                [
                  { key: "kanban", label: "내 칸반", icon: LayoutGrid },
                  { key: "calendar", label: "내 스케줄", icon: CalendarDays },
                  { key: "global", label: "전체 공고 탐색", icon: CalendarDays },
                ] as const
              ).map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setView(key)}
                  className={`relative z-10 flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                    view === key ? "text-foreground" : "text-muted-foreground hover:text-foreground/70"
                  }`}
                >
                  <Icon className="size-4" />
                  {label}
                  {view === key && (
                    <motion.div
                      layoutId="dashboard-view-toggle"
                      className="absolute inset-0 rounded-md bg-background shadow-sm"
                      style={{ zIndex: -1 }}
                      transition={{ type: "spring", duration: 0.35, bounce: 0.15 }}
                    />
                  )}
                </button>
              ))}
            </div>
          </header>

          <main className="flex-1 min-h-0 overflow-hidden bg-muted/30 p-4">
            {view === "kanban" ? <KanbanBoard /> : view === "calendar" ? <RecruitmentCalendar /> : <GlobalRecruitmentCalendar onImportSuccess={() => setView("kanban")} />}
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
