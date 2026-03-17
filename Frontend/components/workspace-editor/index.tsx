import { useEffect } from "react"
import { ContextPanel } from "./context-panel"
import { TranslationPanel } from "./translation-panel"
import { WorkspaceSelector } from "./workspace-selector"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

interface WorkspaceEditorProps {
  applicationId?: string
}

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
