"use client"

import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { ExperienceVault } from "@/components/experience-vault"

export default function VaultPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col">
          <header className="flex h-16 shrink-0 items-center gap-4 border-b border-border bg-background px-8">
            <div>
              <h1 className="text-xl font-semibold tracking-tight">경험 보관소</h1>
              <p className="text-sm text-muted-foreground">
                프로젝트 경험 저장 및 관리
              </p>
            </div>
          </header>
          <main className="flex-1 overflow-auto bg-muted/30 p-6">
            <ExperienceVault />
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
