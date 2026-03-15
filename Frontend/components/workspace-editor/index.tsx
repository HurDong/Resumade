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
    <div className="grid h-full grid-cols-2 overflow-hidden animate-in fade-in duration-500">
      <ContextPanel />
      <TranslationPanel />
    </div>
  )
}
