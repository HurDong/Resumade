"use client"

import { use } from "react"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { WorkspaceEditor } from "@/components/workspace-editor"

export default function WorkspaceDynamicPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>
  searchParams: Promise<{ questionId?: string; q?: string }>
}) {
  const resolvedParams = use(params)
  const resolvedSearch = use(searchParams)
  const id = resolvedParams.id
  const requestedQuestionId = resolvedSearch.questionId ?? resolvedSearch.q
  const initialQuestionDbId = requestedQuestionId ? Number(requestedQuestionId) : undefined

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
            <WorkspaceEditor applicationId={id} initialQuestionDbId={initialQuestionDbId} />
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
