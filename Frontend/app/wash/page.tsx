import { AppSidebar } from "@/components/app-sidebar"
import { DraftWasher } from "@/components/draft-washer"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

export default function WashPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0 min-w-0">
        <div className="h-screen min-h-0">
          <DraftWasher />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
