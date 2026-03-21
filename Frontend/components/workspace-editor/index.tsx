import { useEffect } from "react"
import dynamic from "next/dynamic"
import { Skeleton } from "@/components/ui/skeleton"
import { WorkspaceSelector } from "./workspace-selector"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

interface WorkspaceEditorProps {
  applicationId?: string
}

function WorkspacePanelSkeleton() {
  return (
    <div className="flex h-full min-w-0 flex-col border-border bg-background">
      <div className="shrink-0 border-b border-border p-6">
        <Skeleton className="h-5 w-40" />
        <Skeleton className="mt-3 h-4 w-72 max-w-full" />
      </div>
      <div className="flex-1 space-y-4 p-6">
        <Skeleton className="h-12 w-full rounded-2xl" />
        <Skeleton className="h-32 w-full rounded-3xl" />
        <Skeleton className="h-64 w-full rounded-3xl" />
      </div>
    </div>
  )
}

const ContextPanel = dynamic(
  () => import("./context-panel").then((module) => module.ContextPanel),
  {
    loading: () => <WorkspacePanelSkeleton />,
  }
)

const TranslationPanel = dynamic(
  () => import("./translation-panel").then((module) => module.TranslationPanel),
  {
    loading: () => <WorkspacePanelSkeleton />,
  }
)

export function WorkspaceEditor({ applicationId }: WorkspaceEditorProps) {
  const { fetchApplicationData } = useWorkspaceStore()

  useEffect(() => {
    if (applicationId) {
      fetchApplicationData(applicationId)
    }
  }, [applicationId, fetchApplicationData])

  if (!applicationId) {
    return <WorkspaceSelector />
  }

  return (
    <div className="grid h-full min-w-0 grid-cols-[minmax(0,1fr)_minmax(0,1fr)] overflow-hidden animate-in fade-in duration-500">
      <div className="min-w-0 overflow-hidden">
        <ContextPanel />
      </div>
      <div className="min-w-0 overflow-hidden">
        <TranslationPanel />
      </div>
    </div>
  )
}
