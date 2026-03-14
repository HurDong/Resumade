import { useEffect } from "react"
import { ContextPanel } from "./context-panel"
import { TranslationPanel } from "./translation-panel"
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

  return (
    <div className="grid h-full grid-cols-2">
      <ContextPanel />
      <TranslationPanel />
    </div>
  )
}
