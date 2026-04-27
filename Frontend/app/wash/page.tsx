import { AppSidebar } from "@/components/app-sidebar"
import { DraftWasher } from "@/components/draft-washer"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

export default async function WashPage({
  searchParams,
}: {
  searchParams: Promise<{ applicationId?: string; questionId?: string }>
}) {
  const resolvedSearchParams = await searchParams

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0 min-w-0">
        <div className="h-screen min-h-0">
          <DraftWasher
            initialApplicationId={resolvedSearchParams.applicationId}
            initialQuestionId={resolvedSearchParams.questionId}
          />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
