"use client"

import { Suspense } from "react"
import { useSearchParams } from "next/navigation"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { WorkspaceEditor } from "@/components/workspace-editor"

function WorkspacePageContent() {
  const searchParams = useSearchParams()
  const applicationId = searchParams.get("applicationId") ?? undefined

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col">
          <header className="flex h-16 shrink-0 items-center gap-4 border-b border-border bg-background px-8">
            <div>
              <h1 className="text-xl font-semibold tracking-tight">작업공간</h1>
              <p className="text-sm text-muted-foreground">
                AI 기반 자기소개서 작성 어시스턴트
              </p>
            </div>
          </header>
          <main className="flex-1 overflow-hidden">
            <WorkspaceEditor applicationId={applicationId} />
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

export default function WorkspacePage() {
  return (
    <Suspense fallback={
      <div className="flex h-screen items-center justify-center bg-background">
        <div className="size-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    }>
      <WorkspacePageContent />
    </Suspense>
  )
}
