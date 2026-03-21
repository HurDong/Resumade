"use client"

import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { KanbanBoard } from "@/components/kanban-board"

export default function DashboardPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0">
        <div className="flex h-screen min-h-0 flex-col">
          <header className="flex h-14 shrink-0 items-center border-b border-border bg-background px-6">
            <div>
              <h1 className="text-lg font-semibold tracking-tight">채용 파이프라인</h1>
            </div>
          </header>
          <main className="flex-1 min-h-0 overflow-hidden bg-muted/30 p-4">
            <KanbanBoard />
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
