import { Lightbulb } from "lucide-react"
import { AppSidebar } from "@/components/app-sidebar"
import { CoteWikiEditor } from "@/components/cote-wiki/cote-wiki-editor"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

export default async function CoteWikiEditPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const noteId = Number(id)

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col">
          <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-background px-8">
            <div className="flex flex-col">
              <h1 className="text-xl font-semibold tracking-tight">코테 위키 편집</h1>
              <p className="text-sm text-muted-foreground">
                자유롭게 적되, 전날 빠르게 훑을 수 있는 문서 구조를 유지하세요.
              </p>
            </div>

            <div className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-muted/20 px-3 py-1.5 text-sm text-muted-foreground">
              <Lightbulb className="size-4" />
              자동 저장
            </div>
          </header>

          <main className="flex-1 overflow-auto bg-muted/30 p-6">
            <CoteWikiEditor noteId={noteId} />
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
